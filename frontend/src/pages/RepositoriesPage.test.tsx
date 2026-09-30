import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RepositoriesPage } from './RepositoriesPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { repositoriesApi } from '@/api/repositories'
import { organizationsApi } from '@/api/organizations'

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    list: vi.fn(),
    create: vi.fn(),
    connect: vi.fn(),
  },
}))

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    listMembers: vi.fn(),
  },
}))

describe('RepositoriesPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1234'

  const mockOrg = {
    id: mockOrgId,
    name: 'DevFlow Infrastructure',
    slug: 'devflow-infra',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockAuthContext: AuthContextValue = {
    session: {
      accessToken: 'token_123',
      tokenType: 'Bearer',
      expiresAt: Date.now() + 3600000,
      userId: 'user-admin-111',
      email: 'admin@devflow.local',
      displayName: 'Admin User',
    },
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  const mockOrgContext: OrganizationContextValue = {
    organizations: [mockOrg],
    activeOrganization: mockOrg,
    activeOrganizationId: mockOrgId,
    setActiveOrganizationId: vi.fn(),
    isLoading: false,
    error: null,
    refetchOrganizations: vi.fn(),
  }

  const mockRepositoriesPage = {
    content: [
      {
        id: 'repo-uuid-1',
        organizationId: mockOrgId,
        provider: 'GITHUB' as const,
        externalId: '111222',
        fullName: 'devflow-infra/core-api',
        defaultBranch: 'main',
        active: true,
        createdAt: '2026-09-30T10:00:00Z',
        updatedAt: '2026-09-30T10:00:00Z',
      },
      {
        id: 'repo-uuid-2',
        organizationId: mockOrgId,
        provider: 'GITHUB' as const,
        externalId: '333444',
        fullName: 'devflow-infra/frontend-web',
        defaultBranch: 'develop',
        active: false,
        createdAt: '2026-09-30T11:00:00Z',
        updatedAt: '2026-09-30T11:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
  }

  const mockMembers = {
    content: [
      {
        organizationId: mockOrgId,
        userId: 'user-admin-111',
        email: 'admin@devflow.local',
        displayName: 'Admin User',
        role: 'ADMIN' as const,
        createdAt: '2026-09-30T10:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.mocked(repositoriesApi.list).mockResolvedValue(mockRepositoriesPage)
    vi.mocked(organizationsApi.listMembers).mockResolvedValue(mockMembers)
    vi.clearAllMocks()
  })

  const renderRepositoriesPage = (
    initialEntry = `/organizations/${mockOrgId}/repositories`,
    orgContext = mockOrgContext,
    authContext = mockAuthContext
  ) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={authContext}>
          <OrganizationContext.Provider value={orgContext}>
            <MemoryRouter initialEntries={[initialEntry]}>
              <Routes>
                <Route
                  path="/organizations/:organizationId/repositories"
                  element={<RepositoriesPage />}
                />
                <Route path="/repositories" element={<RepositoriesPage />} />
                <Route
                  path="/organizations"
                  element={<div>Organizations Directory</div>}
                />
              </Routes>
            </MemoryRouter>
          </OrganizationContext.Provider>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders list of repositories with full names, branch badges, and active statuses', async () => {
    renderRepositoriesPage()

    await waitFor(() => {
      expect(screen.getByText('devflow-infra/core-api')).toBeInTheDocument()
      expect(screen.getByText('devflow-infra/frontend-web')).toBeInTheDocument()
      expect(screen.getByText('main')).toBeInTheDocument()
      expect(screen.getByText('develop')).toBeInTheDocument()
      expect(screen.getByText('Active')).toBeInTheDocument()
      expect(screen.getByText('Inactive')).toBeInTheDocument()
      expect(screen.getByText('ext: 111222')).toBeInTheDocument()
    })
  })

  it('renders onboarding empty state when no repositories exist in organization', async () => {
    vi.mocked(repositoriesApi.list).mockResolvedValueOnce({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    })

    renderRepositoriesPage()

    await waitFor(() => {
      expect(screen.getByText('No repositories connected')).toBeInTheDocument()
      expect(
        screen.getByText(/connect a github repository via the devflow github app/i)
      ).toBeInTheDocument()
    })
  })

  it('filters repositories client-side by full name or branch', async () => {
    const user = userEvent.setup()
    renderRepositoriesPage()

    await waitFor(() => {
      expect(screen.getByText('devflow-infra/core-api')).toBeInTheDocument()
    })

    const filterInput = screen.getByPlaceholderText('Filter repositories...')
    await user.type(filterInput, 'frontend')

    expect(screen.queryByText('devflow-infra/core-api')).not.toBeInTheDocument()
    expect(screen.getByText('devflow-infra/frontend-web')).toBeInTheDocument()
  })

  it('renders warning banner when current user is a VIEWER without mutation permissions', async () => {
    const viewerMembers = {
      content: [
        {
          organizationId: mockOrgId,
          userId: 'user-admin-111',
          email: 'admin@devflow.local',
          displayName: 'Admin User',
          role: 'VIEWER' as const,
          createdAt: '2026-09-30T10:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }
    vi.mocked(organizationsApi.listMembers).mockResolvedValueOnce(viewerMembers)

    renderRepositoriesPage()

    await waitFor(() => {
      expect(
        screen.getByText(/only owners and admins have permission to connect or register repositories/i)
      ).toBeInTheDocument()
    })
  })

  it('handles backend error and permits query retry', async () => {
    const user = userEvent.setup()
    vi.mocked(repositoriesApi.list).mockRejectedValueOnce(new Error('Connection failure'))

    renderRepositoriesPage()

    await waitFor(() => {
      expect(screen.getByText('Failed to load repositories')).toBeInTheDocument()
      expect(screen.getByText('Connection failure')).toBeInTheDocument()
    })

    const retryBtn = screen.getByRole('button', { name: /retry/i })
    vi.mocked(repositoriesApi.list).mockResolvedValueOnce(mockRepositoriesPage)
    await user.click(retryBtn)

    await waitFor(() => {
      expect(screen.getByText('devflow-infra/core-api')).toBeInTheDocument()
    })
  })

  it('renders empty state prompting organization selection when no org is active', async () => {
    const emptyOrgContext: OrganizationContextValue = {
      organizations: [],
      activeOrganization: null,
      activeOrganizationId: null,
      setActiveOrganizationId: vi.fn(),
      isLoading: false,
      error: null,
      refetchOrganizations: vi.fn(),
    }

    renderRepositoriesPage('/repositories', emptyOrgContext)

    expect(screen.getByText('No Organization Selected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /view organizations/i })).toBeInTheDocument()
  })

  it('renders pagination controls and navigates pages when totalPages > 1', async () => {
    const user = userEvent.setup()
    const paginatedRepos = {
      ...mockRepositoriesPage,
      page: 0,
      totalPages: 2,
      first: true,
      last: false,
    }
    vi.mocked(repositoriesApi.list).mockResolvedValueOnce(paginatedRepos)

    renderRepositoriesPage()

    await waitFor(() => {
      expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
    })

    const nextBtn = screen.getByRole('button', { name: /next/i })
    expect(nextBtn).toBeEnabled()
    const prevBtn = screen.getByRole('button', { name: /previous/i })
    expect(prevBtn).toBeDisabled()

    await user.click(nextBtn)
    expect(repositoriesApi.list).toHaveBeenCalledWith(mockOrgId, expect.objectContaining({ page: 1 }))
  })
})
