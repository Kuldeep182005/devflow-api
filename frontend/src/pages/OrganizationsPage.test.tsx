import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { OrganizationsPage } from './OrganizationsPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    create: vi.fn(),
  },
}))

describe('OrganizationsPage', () => {
  const mockOrg = {
    id: 'org-abc-123',
    name: 'DevFlow Production',
    slug: 'devflow-prod',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderPage = (contextOverrides: Partial<OrganizationContextValue> = {}) => {
    const defaultContext: OrganizationContextValue = {
      organizations: [mockOrg],
      activeOrganization: mockOrg,
      activeOrganizationId: mockOrg.id,
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
            <OrganizationsPage />
          </OrganizationContext.Provider>
        </MemoryRouter>
      ),
      context: defaultContext,
    }
  }

  it('renders organizations list with details and active status', () => {
    renderPage()

    expect(screen.getByText('DevFlow Production')).toBeInTheDocument()
    expect(screen.getByText('devflow-prod')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /manage & members/i })).toBeInTheDocument()
  })

  it('renders empty state when user belongs to no organizations', () => {
    renderPage({ organizations: [], activeOrganization: null, activeOrganizationId: null })

    expect(screen.getByText('No organizations connected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /create organization/i })).toBeInTheDocument()
  })

  it('renders error state with retry button on failure', () => {
    const refetchMock = vi.fn()
    renderPage({
      error: new Error('Network timeout loading organizations'),
      refetchOrganizations: refetchMock,
    })

    expect(screen.getByText('Could not load organizations')).toBeInTheDocument()
    expect(screen.getByText('Network timeout loading organizations')).toBeInTheDocument()

    const retryBtn = screen.getByRole('button', { name: /retry/i })
    retryBtn.click()
    expect(refetchMock).toHaveBeenCalledTimes(1)
  })

  it('validates client-side constraints in CreateOrganizationDialog', async () => {
    const user = userEvent.setup()
    renderPage()

    const openBtn = screen.getByRole('button', { name: /new organization/i })
    await user.click(openBtn)

    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeInTheDocument()

    // Submit empty form
    const submitBtn = screen.getByRole('button', { name: /^create organization$/i })
    await user.click(submitBtn)

    expect(screen.getByText('Organization name is required')).toBeInTheDocument()
    expect(screen.getByText('Slug is required')).toBeInTheDocument()
    expect(organizationsApi.create).not.toHaveBeenCalled()
  })

  it('creates an organization and updates active context', async () => {
    const user = userEvent.setup()
    const { context } = renderPage()

    vi.mocked(organizationsApi.create).mockResolvedValueOnce({
      id: 'new-org-id',
      name: 'New Engineering Team',
      slug: 'new-engineering-team',
      createdAt: '2026-09-30T12:00:00Z',
      updatedAt: '2026-09-30T12:00:00Z',
    })

    await user.click(screen.getByRole('button', { name: /new organization/i }))

    await user.type(screen.getByLabelText(/organization name/i), 'New Engineering Team')
    // Auto-slug generates 'new-engineering-team'

    const submitBtn = screen.getByRole('button', { name: /^create organization$/i })
    await user.click(submitBtn)

    expect(organizationsApi.create).toHaveBeenCalledWith({
      name: 'New Engineering Team',
      slug: 'new-engineering-team',
    })

    await waitFor(() => {
      expect(context.refetchOrganizations).toHaveBeenCalledTimes(1)
      expect(context.setActiveOrganizationId).toHaveBeenCalledWith('new-org-id')
    })
  })

  it('handles backend 409 conflict when slug already exists', async () => {
    const user = userEvent.setup()
    renderPage()

    vi.mocked(organizationsApi.create).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Organization slug already exists',
        code: 'SLUG_ALREADY_EXISTS',
        status: 409,
        requestId: 'req_org_conflict_1',
      })
    )

    await user.click(screen.getByRole('button', { name: /new organization/i }))

    await user.type(screen.getByLabelText(/organization name/i), 'Duplicate Org')
    await user.click(screen.getByRole('button', { name: /^create organization$/i }))

    await waitFor(() => {
      expect(screen.getByText('This slug is already taken. Please choose another.')).toBeInTheDocument()
      expect(screen.getByText('An organization with this slug already exists.')).toBeInTheDocument()
    })
  })
})
