import React, { useState, useMemo, useCallback } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { organizationsApi } from '@/api/organizations'
import { useAuth } from '@/hooks/useAuth'
import { OrganizationContext, type OrganizationContextValue } from './context'

const ORG_STORAGE_PREFERENCE_KEY = 'devflow_ui_active_org_id'

export const OrganizationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  // Local UI preference state
  const [preferredOrgId, setPreferredOrgId] = useState<string | null>(() => {
    try {
      return localStorage.getItem(ORG_STORAGE_PREFERENCE_KEY)
    } catch {
      return null
    }
  })

  // TanStack Query for organizations list
  const {
    data: organizations = [],
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => organizationsApi.list(),
    enabled: isAuthenticated,
    staleTime: 60 * 1000,
  })

  // Derive active organization safely against the actual backend list
  const activeOrganization = useMemo(() => {
    if (!isAuthenticated || !organizations || organizations.length === 0) return null

    if (preferredOrgId) {
      const match = organizations.find((o) => o.id === preferredOrgId)
      if (match) return match
    }

    // Default to the first available organization
    return organizations[0]
  }, [isAuthenticated, organizations, preferredOrgId])

  const setActiveOrganizationId = useCallback((id: string) => {
    setPreferredOrgId(id)
    try {
      localStorage.setItem(ORG_STORAGE_PREFERENCE_KEY, id)
    } catch {
      // Ignore storage error
    }
  }, [])

  const refetchOrganizations = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['organizations'] })
    refetch()
  }, [queryClient, refetch])

  const value: OrganizationContextValue = {
    organizations: isAuthenticated ? organizations : [],
    activeOrganization,
    activeOrganizationId: activeOrganization ? activeOrganization.id : null,
    setActiveOrganizationId,
    isLoading: isAuthenticated ? isLoading : false,
    error: error instanceof Error ? error : null,
    refetchOrganizations,
  }

  return (
    <OrganizationContext.Provider value={value}>
      {children}
    </OrganizationContext.Provider>
  )
}
