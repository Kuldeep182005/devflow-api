import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { OrganizationSelector } from './OrganizationSelector'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'

describe('OrganizationSelector', () => {
  const mockOrg1 = {
    id: 'org-1',
    name: 'DevFlow Alpha',
    slug: 'devflow-alpha',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockOrg2 = {
    id: 'org-2',
    name: 'DevFlow Beta',
    slug: 'devflow-beta',
    createdAt: '2026-09-30T11:00:00Z',
    updatedAt: '2026-09-30T11:00:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderSelector = (contextOverrides: Partial<OrganizationContextValue> = {}) => {
    const defaultContext: OrganizationContextValue = {
      organizations: [mockOrg1, mockOrg2],
      activeOrganization: mockOrg1,
      activeOrganizationId: mockOrg1.id,
      setActiveOrganizationId: vi.fn(),
      isLoading: false,
      error: null,
      refetchOrganizations: vi.fn(),
      ...contextOverrides,
    }

    return {
      ...render(
        <MemoryRouter>
          <OrganizationContext.Provider value={defaultContext}>
            <OrganizationSelector />
          </OrganizationContext.Provider>
        </MemoryRouter>
      ),
      context: defaultContext,
    }
  }

  it('renders loading placeholder while fetching organizations', () => {
    const { container } = renderSelector({ isLoading: true })
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument()
  })

  it('renders active organization name and slug', () => {
    renderSelector()
    expect(screen.getByText('DevFlow Alpha')).toBeInTheDocument()
    expect(screen.getByText('devflow-alpha')).toBeInTheDocument()
  })

  it('renders fallback when no organization is active/present', () => {
    renderSelector({ organizations: [], activeOrganization: null, activeOrganizationId: null })
    expect(screen.getByText('No Organization')).toBeInTheDocument()
    expect(screen.getByText('Create one to begin')).toBeInTheDocument()
  })

  it('opens dropdown and allows switching active organization', async () => {
    const user = userEvent.setup()
    const { context } = renderSelector()

    const triggerBtn = screen.getByRole('button', { name: /select organization/i })
    await user.click(triggerBtn)

    expect(screen.getByRole('listbox')).toBeInTheDocument()
    expect(screen.getByText('DevFlow Beta')).toBeInTheDocument()

    const org2Option = screen.getByRole('option', { name: /devflow beta/i })
    await user.click(org2Option)

    expect(context.setActiveOrganizationId).toHaveBeenCalledWith('org-2')
  })

  it('opens create organization dialog from dropdown', async () => {
    const user = userEvent.setup()
    renderSelector()

    await user.click(screen.getByRole('button', { name: /select organization/i }))

    const createBtn = screen.getByRole('button', { name: /create organization/i })
    await user.click(createBtn)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Create New Organization')).toBeInTheDocument()
  })
})
