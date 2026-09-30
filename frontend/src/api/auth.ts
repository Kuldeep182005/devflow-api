import { api } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types/api'

export const authApi = {
  login: (data: LoginRequest): Promise<AuthResponse> => {
    return api.post<AuthResponse>('/api/v1/auth/login', data)
  },

  register: (data: RegisterRequest): Promise<AuthResponse> => {
    return api.post<AuthResponse>('/api/v1/auth/register', data)
  },
}
