import { useContext } from 'react'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'

export function useActiveOrganization(): OrganizationContextValue {
  const context = useContext(OrganizationContext)
  if (!context) {
    throw new Error('useActiveOrganization must be used within an OrganizationProvider')
  }
  return context
}
