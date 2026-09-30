import { createContext } from 'react'
import type { AuthResponse, StoredUserSession } from '@/types/api'

export interface AuthContextValue {
  session: StoredUserSession | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (auth: AuthResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
