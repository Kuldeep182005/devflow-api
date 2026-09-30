import { api } from './client'
import type { HealthResponse } from '@/types/api'

export const healthApi = {
  check: (): Promise<HealthResponse> => {
    return api.get<HealthResponse>('/api/v1/health')
  },
}
