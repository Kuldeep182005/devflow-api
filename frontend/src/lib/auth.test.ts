import { describe, it, expect, beforeEach } from 'vitest'
import { getStoredAuth, setStoredAuth, clearStoredAuth, getAuthToken } from './auth'
import type { AuthResponse } from '@/types/api'

describe('Auth storage library', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores and retrieves authenticated session', () => {
    const mockAuth: AuthResponse = {
      accessToken: 'jwt_mock_token_123',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Lead Engineer',
    }

    const session = setStoredAuth(mockAuth)
    expect(session.accessToken).toBe('jwt_mock_token_123')
    expect(session.email).toBe('engineer@devflow.local')

    const retrieved = getStoredAuth()
    expect(retrieved).not.toBeNull()
    expect(retrieved?.accessToken).toBe('jwt_mock_token_123')
    expect(getAuthToken()).toBe('jwt_mock_token_123')
  })

  it('clears stored session properly', () => {
    const mockAuth: AuthResponse = {
      accessToken: 'jwt_mock_token_123',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Lead Engineer',
    }

    setStoredAuth(mockAuth)
    clearStoredAuth()

    expect(getStoredAuth()).toBeNull()
    expect(getAuthToken()).toBeNull()
  })

  it('returns null if token has expired', () => {
    const expiredSession = {
      accessToken: 'expired_token',
      tokenType: 'Bearer',
      expiresAt: Date.now() - 5000, // expired 5 seconds ago
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Lead Engineer',
    }

    localStorage.setItem('devflow_auth_session', JSON.stringify(expiredSession))
    expect(getStoredAuth()).toBeNull()
  })
})
