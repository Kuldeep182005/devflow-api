import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TasksPage } from './TasksPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { tasksApi } from '@/api/tasks'
import { repositoriesApi } from '@/api/repositories'
import { organizationsApi } from '@/api/organizations'

vi.mock('@/api/tasks', () => ({
  tasksApi: {
    list: vi.fn(),
    create: vi.fn(),
  },
}))

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    listMembers: vi.fn(),
  },
}))

describe('TasksPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1234'

  const mockOrg = {
    id: mockOrgId,
    name: 'DevFlow Test Org',
    slug: 'devflow-test-org',
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
    ],
    totalElements: 1,
    totalPages: 1,
    size: 100,
    page: 0,
    first: true,
    last: true,
  }

  const mockTasksPage = {
    content: [
      {
        id: 'task-uuid-1',
        organizationId: mockOrgId,
        repositoryId: 'repo-uuid-1',
        createdByUserId: 'user-admin-111',
        sourceType: 'MANUAL' as const,
        sourceReference: null,
        title: 'Fix authorization token validation',
        description: 'Resolve token edge case with refresh',
        status: 'RUNNING' as const,
        branchName: 'devflow/fix-auth-token',
        currentStep: 'Running Docker Sandbox',
        failureCode: null,
        failureMessage: null,
        failedAt: null,
        createdAt: '2026-09-30T11:00:00Z',
        updatedAt: '2026-09-30T11:05:00Z',
        startedAt: '2026-09-30T11:00:05Z',
        finishedAt: null,
      },
      {
        id: 'task-uuid-2',
        organizationId: mockOrgId,
        repositoryId: 'repo-uuid-1',
        createdByUserId: 'user-admin-111',
        sourceType: 'GITHUB_ISSUE' as const,
        sourceReference: '#42',
        title: 'Add support for webhooks',
        description: null,
        status: 'SUCCEEDED' as const,
        branchName: 'devflow/webhook-support',
        currentStep: null,
        failureCode: null,
        failureMessage: null,
        failedAt: null,
        createdAt: '2026-09-30T09:00:00Z',
        updatedAt: '2026-09-30T09:15:00Z',
        startedAt: '2026-09-30T09:00:05Z',
        finishedAt: '2026-09-30T09:15:00Z',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    size: 15,
    page: 0,
    first: true,
    last: true,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    vi.mocked(repositoriesApi.list).mockResolvedValue(mockRepositoriesPage)
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [
        {
          organizationId: mockOrgId,
          userId: 'user-admin-111',
          email: 'admin@devflow.local',
          displayName: 'Admin User',
          role: 'ADMIN',
          createdAt: '2026-09-30T10:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      page: 0,
      first: true,
      last: true,
    })
  })

  const renderComponent = (orgContext = mockOrgContext) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={mockAuthContext}>
          <OrganizationContext.Provider value={orgContext}>
            <MemoryRouter initialEntries={[`/organizations/${mockOrgId}/tasks`]}>
              <Routes>
                <Route path="/organizations/:organizationId/tasks" element={<TasksPage />} />
              </Routes>
            </MemoryRouter>
          </OrganizationContext.Provider>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders loading state initially', () => {
    vi.mocked(tasksApi.list).mockReturnValue(new Promise(() => {}))
    renderComponent()
    expect(screen.getByText(/Loading task records from backend/i)).toBeInTheDocument()
  })

  it('renders tasks successfully with status badges and repository info', async () => {
    vi.mocked(tasksApi.list).mockResolvedValue(mockTasksPage)
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Fix authorization token validation')).toBeInTheDocument()
      expect(screen.getByText('Add support for webhooks')).toBeInTheDocument()
    })

    expect(screen.getByText('Running')).toBeInTheDocument()
    expect(screen.getByText('Succeeded')).toBeInTheDocument()
    expect(screen.getAllByText('devflow-infra/core-api').length).toBeGreaterThan(0)
  })

  it('renders empty state when organization has no tasks', async () => {
    vi.mocked(tasksApi.list).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 15,
      page: 0,
      first: true,
      last: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('No Tasks Dispatched Yet')).toBeInTheDocument()
    })
  })

  it('filters by repository when dropdown changes', async () => {
    const user = userEvent.setup()
    vi.mocked(tasksApi.list).mockResolvedValue(mockTasksPage)
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Fix authorization token validation')).toBeInTheDocument()
    })

    const filterSelect = screen.getByLabelText('Filter tasks by repository')
    await user.selectOptions(filterSelect, 'repo-uuid-1')

    await waitFor(() => {
      expect(tasksApi.list).toHaveBeenCalledWith(mockOrgId, {
        repositoryId: 'repo-uuid-1',
        page: 0,
        size: 15,
        sort: 'createdAt,desc',
      })
    })
  })

  it('handles API error and displays retry button', async () => {
    vi.mocked(tasksApi.list).mockRejectedValue(new Error('Network error loading tasks'))
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Failed to Load Tasks')).toBeInTheDocument()
      expect(screen.getByText('Network error loading tasks')).toBeInTheDocument()
    })
  })
})
