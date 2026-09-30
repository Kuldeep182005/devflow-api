import React, { useState, useEffect, useCallback } from 'react'
import type { AuthResponse, StoredUserSession } from '@/types/api'
import { getStoredAuth, setStoredAuth, clearStoredAuth } from '@/lib/auth'
import { api } from '@/api/client'
import { AuthContext, type AuthContextValue } from './context'

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<StoredUserSession | null>(() => getStoredAuth())
  const [isLoading] = useState<boolean>(false)

  const logout = useCallback(() => {
    clearStoredAuth()
    setSession(null)
  }, [])

  useEffect(() => {
    // Register 401 callback in API client so expired sessions trigger automatic logout
    api.setUnauthorizedHandler(() => {
      logout()
    })
  }, [logout])

  const login = useCallback((auth: AuthResponse) => {
    const newSession = setStoredAuth(auth)
    setSession(newSession)
  }, [])

  const value: AuthContextValue = {
    session,
    isAuthenticated: !!session && !!session.accessToken,
    isLoading,
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
