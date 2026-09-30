import type { AuthResponse, StoredUserSession } from '@/types/api'

const AUTH_STORAGE_KEY = 'devflow_auth_session'

/**
 * Retrieves the stored session from localStorage if present and not expired.
 */
export function getStoredAuth(): StoredUserSession | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null

    const session: StoredUserSession = JSON.parse(raw)
    // Check if token expiration has passed (with 10-second margin)
    if (session.expiresAt && Date.now() > session.expiresAt - 10000) {
      clearStoredAuth()
      return null
    }

    return session
  } catch {
    clearStoredAuth()
    return null
  }
}

/**
 * Stores a successful authentication response.
 */
export function setStoredAuth(auth: AuthResponse): StoredUserSession {
  const expiresAt = Date.now() + auth.expiresIn * 1000
  const session: StoredUserSession = {
    accessToken: auth.accessToken,
    tokenType: auth.tokenType || 'Bearer',
    expiresAt,
    userId: auth.userId,
    email: auth.email,
    displayName: auth.displayName,
  }

  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
  } catch (error) {
    console.warn('Unable to persist session to localStorage', error)
  }

  return session
}

/**
 * Clears the stored session.
 */
export function clearStoredAuth(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // Ignore storage errors on cleanup
  }
}

/**
 * Get current access token if session is valid.
 */
export function getAuthToken(): string | null {
  const session = getStoredAuth()
  return session ? session.accessToken : null
}
