import { api } from './client'
import type {
  TaskResponse,
  CreateTaskRequest,
  TaskEventResponse,
  PageResponse,
} from '@/types/api'

export const tasksApi = {
  list: (
    organizationId: string,
    params?: {
      repositoryId?: string
      page?: number
      size?: number
      sort?: string
    }
  ): Promise<PageResponse<TaskResponse>> => {
    return api.get<PageResponse<TaskResponse>>(
      `/api/v1/organizations/${organizationId}/tasks`,
      { params }
    )
  },

  get: (organizationId: string, taskId: string): Promise<TaskResponse> => {
    return api.get<TaskResponse>(
      `/api/v1/organizations/${organizationId}/tasks/${taskId}`
    )
  },

  create: (
    organizationId: string,
    data: CreateTaskRequest
  ): Promise<TaskResponse> => {
    return api.post<TaskResponse>(
      `/api/v1/organizations/${organizationId}/tasks`,
      data
    )
  },

  cancel: (
    organizationId: string,
    taskId: string,
    idempotencyKey?: string
  ): Promise<TaskResponse> => {
    return api.post<TaskResponse>(
      `/api/v1/organizations/${organizationId}/tasks/${taskId}/cancel`,
      undefined,
      { idempotencyKey }
    )
  },

  events: (
    organizationId: string,
    taskId: string,
    params?: {
      page?: number
      size?: number
      sort?: string
    }
  ): Promise<PageResponse<TaskEventResponse>> => {
    return api.get<PageResponse<TaskEventResponse>>(
      `/api/v1/organizations/${organizationId}/tasks/${taskId}/events`,
      { params }
    )
  },
}
