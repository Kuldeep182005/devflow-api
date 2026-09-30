import { api } from './client'
import type {
  RepositoryResponse,
  CreateRepositoryRequest,
  ConnectRepositoryRequest,
  PageResponse,
} from '@/types/api'

export const repositoriesApi = {
  list: (
    organizationId: string,
    params?: { page?: number; size?: number; sort?: string }
  ): Promise<PageResponse<RepositoryResponse>> => {
    return api.get<PageResponse<RepositoryResponse>>(
      `/api/v1/organizations/${organizationId}/repositories`,
      { params }
    )
  },

  get: (organizationId: string, repositoryId: string): Promise<RepositoryResponse> => {
    return api.get<RepositoryResponse>(
      `/api/v1/organizations/${organizationId}/repositories/${repositoryId}`
    )
  },

  create: (
    organizationId: string,
    data: CreateRepositoryRequest
  ): Promise<RepositoryResponse> => {
    return api.post<RepositoryResponse>(
      `/api/v1/organizations/${organizationId}/repositories`,
      data
    )
  },

  connect: (
    organizationId: string,
    data: ConnectRepositoryRequest
  ): Promise<RepositoryResponse> => {
    return api.post<RepositoryResponse>(
      `/api/v1/organizations/${organizationId}/repositories/connect`,
      data
    )
  },
}
