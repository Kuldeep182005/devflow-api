import { createContext } from 'react'
import type { OrganizationResponse } from '@/types/api'

export interface OrganizationContextValue {
  organizations: OrganizationResponse[]
  activeOrganization: OrganizationResponse | null
  activeOrganizationId: string | null
  setActiveOrganizationId: (id: string) => void
  isLoading: boolean
  error: Error | null
  refetchOrganizations: () => void
}

export const OrganizationContext = createContext<OrganizationContextValue | undefined>(undefined)
