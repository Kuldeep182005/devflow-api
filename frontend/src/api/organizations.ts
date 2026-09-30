import { api } from './client'
import type {
  OrganizationResponse,
  CreateOrganizationRequest,
  PageResponse,
  OrganizationMemberResponse,
  AddOrganizationMemberRequest,
  UpdateOrganizationMemberRequest,
} from '@/types/api'

export const organizationsApi = {
  list: (): Promise<OrganizationResponse[]> => {
    return api.get<OrganizationResponse[]>('/api/v1/organizations')
  },

  get: (organizationId: string): Promise<OrganizationResponse> => {
    return api.get<OrganizationResponse>(`/api/v1/organizations/${organizationId}`)
  },

  create: (data: CreateOrganizationRequest): Promise<OrganizationResponse> => {
    return api.post<OrganizationResponse>('/api/v1/organizations', data)
  },

  listMembers: (
    organizationId: string,
    params?: { page?: number; size?: number; sort?: string }
  ): Promise<PageResponse<OrganizationMemberResponse>> => {
    return api.get<PageResponse<OrganizationMemberResponse>>(
      `/api/v1/organizations/${organizationId}/members`,
      { params }
    )
  },

  addMember: (
    organizationId: string,
    data: AddOrganizationMemberRequest
  ): Promise<OrganizationMemberResponse> => {
    return api.post<OrganizationMemberResponse>(
      `/api/v1/organizations/${organizationId}/members`,
      data
    )
  },

  updateMember: (
    organizationId: string,
    userId: string,
    data: UpdateOrganizationMemberRequest
  ): Promise<OrganizationMemberResponse> => {
    return api.patch<OrganizationMemberResponse>(
      `/api/v1/organizations/${organizationId}/members/${userId}`,
      data
    )
  },

  removeMember: (organizationId: string, userId: string): Promise<void> => {
    return api.delete<void>(`/api/v1/organizations/${organizationId}/members/${userId}`)
  },
}
