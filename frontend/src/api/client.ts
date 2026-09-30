import type { ApiError, FieldViolation } from '@/types/api'
import { getAuthToken, clearStoredAuth } from '@/lib/auth'
import { generateRequestId } from '@/lib/utils'

export class ApiClientError extends Error {
  readonly code: string
  readonly status: number
  readonly requestId: string
  readonly violations: FieldViolation[]
  readonly retryAfterSeconds?: number

  constructor(params: {
    message: string
    code: string
    status: number
    requestId?: string
    violations?: FieldViolation[]
    retryAfterSeconds?: number
  }) {
    super(params.message)
    this.name = 'ApiClientError'
    this.code = params.code
    this.status = params.status
    this.requestId = params.requestId || 'unknown'
    this.violations = params.violations || []
    this.retryAfterSeconds = params.retryAfterSeconds
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isNotFound(): boolean {
    return this.status === 404
  }

  get isConflict(): boolean {
    return this.status === 409
  }

  get isRateLimited(): boolean {
    return this.status === 429
  }

  get isValidationError(): boolean {
    return this.code === 'VALIDATION_ERROR' || this.violations.length > 0
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  params?: Record<string, string | number | boolean | undefined | null>
  timeoutMs?: number
  idempotencyKey?: string
}

// In-flight mutation tracker to prevent accidental duplicate simultaneous submissions
const activeMutations = new Set<string>()

export class ApiClient {
  private baseUrl: string
  private onUnauthorized?: () => void

  constructor(baseUrl?: string) {
    // When running with Vite dev server proxy or same origin, default to empty string
    this.baseUrl = (baseUrl || import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  }

  public setUnauthorizedHandler(handler: () => void): void {
    this.onUnauthorized = handler
  }

  public async get<T>(path: string, options?: RequestOptions): Promise<T> {
    return this.request<T>('GET', path, options)
  }

  public async post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>('POST', path, { ...options, body })
  }

  public async put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>('PUT', path, { ...options, body })
  }

  public async patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>('PATCH', path, { ...options, body })
  }

  public async delete<T>(path: string, options?: RequestOptions): Promise<T> {
    return this.request<T>('DELETE', path, options)
  }

  private async request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
    path: string,
    options: RequestOptions = {}
  ): Promise<T> {
    const isMutation = method !== 'GET'
    const mutationKey = `${method}:${path}:${JSON.stringify(options.body || '')}`

    if (isMutation) {
      if (activeMutations.has(mutationKey)) {
        throw new ApiClientError({
          message: 'A duplicate request is already in flight. Please wait.',
          code: 'DUPLICATE_SUBMISSION',
          status: 409,
        })
      }
      activeMutations.add(mutationKey)
    }

    try {
      return await this.executeRequest<T>(method, path, options)
    } finally {
      if (isMutation) {
        activeMutations.delete(mutationKey)
      }
    }
  }

  private async executeRequest<T>(
    method: string,
    path: string,
    options: RequestOptions
  ): Promise<T> {
    const { params, body, headers = {}, timeoutMs = 15000, idempotencyKey, ...fetchInit } = options

    // Construct full URL with search params
    const normalizedPath = path.startsWith('/') ? path : `/${path}`
    const url = new URL(`${this.baseUrl}${normalizedPath}`, window.location.origin)

    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          url.searchParams.append(key, String(value))
        }
      })
    }

    const clientRequestId = generateRequestId()
    const requestHeaders: Record<string, string> = {
      'Accept': 'application/json',
      'X-Request-Id': clientRequestId,
      ...(headers as Record<string, string>),
    }

    if (body !== undefined) {
      requestHeaders['Content-Type'] = 'application/json'
    }

    const token = getAuthToken()
    if (token && !requestHeaders['Authorization']) {
      requestHeaders['Authorization'] = `Bearer ${token}`
    }

    if (idempotencyKey) {
      requestHeaders['Idempotency-Key'] = idempotencyKey
    }

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs)

    try {
      const response = await fetch(url.toString(), {
        ...fetchInit,
        method,
        headers: requestHeaders,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      })

      clearTimeout(timeoutId)

      const serverRequestId = response.headers.get('X-Request-Id') || clientRequestId
      const contentType = response.headers.get('content-type') || ''
      const isJson = contentType.includes('application/json')

      // Handle 204 No Content
      if (response.status === 204) {
        return undefined as unknown as T
      }

      // Handle Success
      if (response.ok) {
        if (isJson) {
          return (await response.json()) as T
        }
        return (await response.text()) as unknown as T
      }

      // Handle Errors
      let errorData: Partial<ApiError> = {}
      if (isJson) {
        try {
          errorData = await response.json()
        } catch {
          // Fall back to empty error data
        }
      }

      // Handle Rate Limiting
      let retryAfter: number | undefined
      const retryHeader = response.headers.get('Retry-After')
      if (retryHeader) {
        const parsed = parseInt(retryHeader, 10)
        if (!isNaN(parsed)) retryAfter = parsed
      }

      // Handle 401 Unauthorized
      if (response.status === 401) {
        clearStoredAuth()
        if (this.onUnauthorized) {
          this.onUnauthorized()
        }
      }

      const safeMessage = this.sanitizeErrorMessage(
        errorData.message || response.statusText || 'An unexpected error occurred',
        response.status
      )

      throw new ApiClientError({
        message: safeMessage,
        code: errorData.code || this.defaultErrorCode(response.status),
        status: response.status,
        requestId: errorData.requestId || serverRequestId,
        violations: errorData.violations || [],
        retryAfterSeconds: retryAfter,
      })
    } catch (err: unknown) {
      clearTimeout(timeoutId)

      if (err instanceof ApiClientError) {
        throw err
      }

      if (err instanceof DOMException && err.name === 'AbortError') {
        throw new ApiClientError({
          message: 'The request timed out. Please try again.',
          code: 'TIMEOUT',
          status: 408,
          requestId: clientRequestId,
        })
      }

      throw new ApiClientError({
        message: 'Unable to connect to DevFlow API. Check network connection.',
        code: 'NETWORK_ERROR',
        status: 0,
        requestId: clientRequestId,
      })
    }
  }

  /**
   * Sanitizes error message so internal exceptions, SQL details, and paths are never displayed.
   */
  private sanitizeErrorMessage(rawMessage: string, status: number): string {
    const lower = rawMessage.toLowerCase()
    if (
      lower.includes('exception') ||
      lower.includes('sql') ||
      lower.includes('hibernate') ||
      lower.includes('org.springframework') ||
      lower.includes('stacktrace') ||
      lower.includes('c:\\') ||
      lower.includes('/var/')
    ) {
      if (status === 500) return 'An internal server error occurred. Please contact support.'
      if (status === 503) return 'DevFlow service is temporarily unavailable.'
      return 'The request could not be completed.'
    }
    return rawMessage
  }

  private defaultErrorCode(status: number): string {
    switch (status) {
      case 400:
        return 'BAD_REQUEST'
      case 401:
        return 'UNAUTHORIZED'
      case 403:
        return 'FORBIDDEN'
      case 404:
        return 'NOT_FOUND'
      case 409:
        return 'CONFLICT'
      case 422:
        return 'UNPROCESSABLE_ENTITY'
      case 429:
        return 'RATE_LIMIT_EXCEEDED'
      case 503:
        return 'SERVICE_UNAVAILABLE'
      default:
        return 'INTERNAL_ERROR'
    }
  }
}

// Global default instance
export const api = new ApiClient()
