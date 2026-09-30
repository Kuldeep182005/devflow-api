import { api } from './client'
import type {
  ApprovalResponse,
  RequestApprovalRequest,
  ApproveApprovalRequest,
  RejectApprovalRequest,
  GithubMutationResponse,
  PageResponse,
} from '@/types/api'

export const approvalsApi = {
  list: (
    organizationId: string,
    params?: {
      taskId?: string
      proposedChangeId?: string
      page?: number
      size?: number
      sort?: string
    }
  ): Promise<PageResponse<ApprovalResponse>> => {
    return api.get<PageResponse<ApprovalResponse>>(
      `/api/v1/organizations/${organizationId}/approvals`,
      { params }
    )
  },

  get: (organizationId: string, approvalId: string): Promise<ApprovalResponse> => {
    return api.get<ApprovalResponse>(
      `/api/v1/organizations/${organizationId}/approvals/${approvalId}`
    )
  },

  requestApproval: (
    organizationId: string,
    data: RequestApprovalRequest
  ): Promise<ApprovalResponse> => {
    return api.post<ApprovalResponse>(
      `/api/v1/organizations/${organizationId}/approvals`,
      data
    )
  },

  approve: (
    organizationId: string,
    approvalId: string,
    data?: ApproveApprovalRequest
  ): Promise<ApprovalResponse> => {
    return api.post<ApprovalResponse>(
      `/api/v1/organizations/${organizationId}/approvals/${approvalId}/approve`,
      data || {}
    )
  },

  reject: (
    organizationId: string,
    approvalId: string,
    data: RejectApprovalRequest
  ): Promise<ApprovalResponse> => {
    return api.post<ApprovalResponse>(
      `/api/v1/organizations/${organizationId}/approvals/${approvalId}/reject`,
      data
    )
  },

  execute: (
    organizationId: string,
    approvalId: string
  ): Promise<GithubMutationResponse> => {
    return api.post<GithubMutationResponse>(
      `/api/v1/organizations/${organizationId}/approvals/${approvalId}/execute`
    )
  },

  getMutation: (
    organizationId: string,
    approvalId: string
  ): Promise<GithubMutationResponse> => {
    return api.get<GithubMutationResponse>(
      `/api/v1/organizations/${organizationId}/approvals/${approvalId}/mutation`
    )
  },
}
