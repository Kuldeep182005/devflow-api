import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ApiClient, ApiClientError } from './client'
import { setStoredAuth } from '@/lib/auth'

describe('ApiClient', () => {
  let client: ApiClient

  beforeEach(() => {
    localStorage.clear()
    client = new ApiClient('http://localhost:8080')
    vi.restoreAllMocks()
  })

  it('attaches Bearer token and correlation X-Request-Id header to requests', async () => {
    setStoredAuth({
      accessToken: 'token_xyz_456',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'test@devflow.local',
      displayName: 'Dev',
    })

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 'UP' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'srv-req-123' },
      })
    )

    const result = await client.get<{ status: string }>('/api/v1/health')

    expect(result.status).toBe('UP')
    expect(fetchSpy).toHaveBeenCalledTimes(1)

    const [callUrl, callOptions] = fetchSpy.mock.calls[0]
    expect(callUrl).toContain('/api/v1/health')

    const headers = callOptions?.headers as Record<string, string>
    expect(headers['Authorization']).toBe('Bearer token_xyz_456')
    expect(headers['X-Request-Id']).toMatch(/^req_[a-z0-9]+$/)
  })

  it('parses structured backend ApiError response format', async () => {
    const errorBody = {
      code: 'VALIDATION_ERROR',
      message: 'Request validation failed',
      timestamp: '2026-09-30T15:00:00Z',
      requestId: 'test-req-789',
      violations: [{ field: 'email', message: 'must not be blank' }],
    }

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(errorBody), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      })
    )

    try {
      await client.post('/api/v1/auth/login', {})
      expect.unreachable('Expected request to fail')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiClientError)
      const apiErr = err as ApiClientError
      expect(apiErr.code).toBe('VALIDATION_ERROR')
      expect(apiErr.status).toBe(400)
      expect(apiErr.violations).toHaveLength(1)
      expect(apiErr.violations[0].field).toBe('email')
      expect(apiErr.isValidationError).toBe(true)
    }
  })

  it('sanitizes internal server exceptions to prevent data leaks', async () => {
    const internalLeak = {
      code: 'INTERNAL_ERROR',
      message: 'org.springframework.dao.DataIntegrityViolationException: SQL syntax error near column',
      timestamp: '2026-09-30T15:00:00Z',
      requestId: 'test-req-err',
    }

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(internalLeak), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      })
    )

    try {
      await client.get('/api/v1/organizations')
      expect.unreachable('Should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiClientError)
      const apiErr = err as ApiClientError
      expect(apiErr.message).not.toContain('SQL')
      expect(apiErr.message).not.toContain('org.springframework')
      expect(apiErr.message).toBe('An internal server error occurred. Please contact support.')
    }
  })

  it('triggers unauthorized callback and clears session on 401 response', async () => {
    const onUnauthorizedSpy = vi.fn()
    client.setUnauthorizedHandler(onUnauthorizedSpy)

    setStoredAuth({
      accessToken: 'stale_token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'test@devflow.local',
      displayName: 'Dev',
    })

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: 'Authentication is required' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      })
    )

    await expect(client.get('/api/v1/organizations')).rejects.toThrow(ApiClientError)
    expect(onUnauthorizedSpy).toHaveBeenCalledTimes(1)
  })

  it('prevents accidental duplicate in-flight mutations', async () => {
    let resolveFirstFetch: (res: Response) => void
    const firstPromise = new Promise<Response>((resolve) => {
      resolveFirstFetch = resolve
    })

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() => firstPromise)

    const mutationData = { name: 'DevFlow Org', slug: 'devflow-org' }

    // Start first mutation
    const req1 = client.post('/api/v1/organizations', mutationData)

    // Attempt identical second mutation while first is still pending
    await expect(client.post('/api/v1/organizations', mutationData)).rejects.toThrow(
      'A duplicate request is already in flight. Please wait.'
    )

    // Complete first mutation
    resolveFirstFetch!(
      new Response(JSON.stringify({ id: 'org-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      })
    )

    await expect(req1).resolves.toEqual({ id: 'org-1' })
  })
})
