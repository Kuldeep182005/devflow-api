import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DashboardPage } from './DashboardPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import { approvalsApi } from '@/api/approvals'

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/tasks', () => ({
  tasksApi: {
    list: vi.fn(),
    events: vi.fn(),
    create: vi.fn(),
  },
}))

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    list: vi.fn(),
  },
}))

describe('DashboardPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1111'

  const mockOrg = {
    id: mockOrgId,
    name: 'DevFlow Production Org',
    slug: 'devflow-production-org',
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
      displayName: 'Admin Engineer',
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

  const mockRepositories = [
    {
      id: 'repo-1',
      organizationId: mockOrgId,
      provider: 'GITHUB' as const,
      externalId: '1001',
      fullName: 'devflow-platform/auth-service',
      defaultBranch: 'main',
      active: true,
      createdAt: '2026-09-30T10:00:00Z',
      updatedAt: '2026-09-30T10:00:00Z',
    },
    {
      id: 'repo-2',
      organizationId: mockOrgId,
      provider: 'GITHUB' as const,
      externalId: '1002',
      fullName: 'devflow-platform/worker-daemon',
      defaultBranch: 'main',
      active: true,
      createdAt: '2026-09-30T10:00:00Z',
      updatedAt: '2026-09-30T10:00:00Z',
    },
  ]

  const mockTasks = [
    {
      id: 'task-1',
      organizationId: mockOrgId,
      repositoryId: 'repo-1',
      createdByUserId: 'user-admin-111',
      sourceType: 'MANUAL' as const,
      sourceReference: null,
      title: 'Fix token expiry validation in auth filter',
      description: null,
      status: 'RUNNING' as const,
      branchName: 'devflow/task-1',
      currentStep: 'Executing sandbox',
      failureCode: null,
      failureMessage: null,
      failedAt: null,
      createdAt: '2026-09-30T11:00:00Z',
      updatedAt: '2026-09-30T11:05:00Z',
      startedAt: '2026-09-30T11:01:00Z',
      finishedAt: null,
    },
    {
      id: 'task-2',
      organizationId: mockOrgId,
      repositoryId: 'repo-1',
      createdByUserId: 'user-admin-111',
      sourceType: 'MANUAL' as const,
      sourceReference: null,
      title: 'Implement database connection pooling',
      description: null,
      status: 'SUCCEEDED' as const,
      branchName: 'devflow/task-2',
      currentStep: null,
      failureCode: null,
      failureMessage: null,
      failedAt: null,
      createdAt: '2026-09-30T10:30:00Z',
      updatedAt: '2026-09-30T10:45:00Z',
      startedAt: '2026-09-30T10:31:00Z',
      finishedAt: '2026-09-30T10:45:00Z',
    },
  ]

  const mockApprovals = [
    {
      id: 'approval-1',
      proposedChangeId: 'proposal-1',
      taskId: 'task-1',
      repositoryId: 'repo-1',
      organizationId: mockOrgId,
      status: 'PENDING' as const,
      proposedChangeVersion: 1,
      sandboxExecutionId: 'sandbox-1',
      sandboxExecutionStatus: 'SUCCEEDED' as const,
      requestedByUserId: 'user-admin-111',
      decidedByUserId: null,
      decisionReason: null,
      requestedAt: '2026-09-30T11:05:00Z',
      decidedAt: null,
      createdAt: '2026-09-30T11:05:00Z',
      updatedAt: '2026-09-30T11:05:00Z',
      version: 1,
    },
  ]

  const mockEvents = [
    {
      id: 'event-1',
      taskId: 'task-1',
      organizationId: mockOrgId,
      eventType: 'GITHUB_PR_CREATED' as const,
      previousState: 'RUNNING' as const,
      newState: 'SUCCEEDED' as const,
      actorSource: 'WORKER' as const,
      actorUserId: null,
      occurredAt: '2026-09-30T11:04:00Z',
      correlationId: 'corr-1',
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })

    vi.mocked(repositoriesApi.list).mockResolvedValue({
      content: mockRepositories,
      totalElements: 2,
      totalPages: 1,
      size: 100,
      page: 0,
      first: true,
      last: true,
    })
    vi.mocked(tasksApi.list).mockResolvedValue({
      content: mockTasks,
      totalElements: 2,
      totalPages: 1,
      size: 100,
      page: 0,
      first: true,
      last: true,
    })
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: mockApprovals,
      totalElements: 1,
      totalPages: 1,
      size: 100,
      page: 0,
      first: true,
      last: true,
    })
    vi.mocked(tasksApi.events).mockResolvedValue({
      content: mockEvents,
      totalElements: 1,
      totalPages: 1,
      size: 5,
      page: 0,
      first: true,
      last: true,
    })
  })

  const renderComponent = (orgContextOverrides?: Partial<OrganizationContextValue>) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AuthContext.Provider value={mockAuthContext}>
            <OrganizationContext.Provider value={{ ...mockOrgContext, ...orgContextOverrides }}>
              <DashboardPage />
            </OrganizationContext.Provider>
          </AuthContext.Provider>
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  it('renders engineering overview with authoritative metric counts and active workspace', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Engineering Overview')).toBeInTheDocument()
      expect(screen.getAllByText('DevFlow Production Org').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('API Connected')).toBeInTheDocument()
      // Wait for queries to resolve and render real values
      expect(screen.getByRole('link', { name: /Connected Repositories: 2/i })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /Active Tasks: 1/i })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /Awaiting Approval: 1/i })).toBeInTheDocument()
    })

    // Metric cards titles
    expect(screen.getByText('Active Tasks')).toBeInTheDocument()
    expect(screen.getByText('Awaiting Approval')).toBeInTheDocument()
    expect(screen.getByText('Connected Repositories')).toBeInTheDocument()
    expect(screen.getByText('Completed Tasks')).toBeInTheDocument()
  })

  it('renders recent engineering activity feed from task state machine events', async () => {
    renderComponent()

    await waitFor(() => {
      expect(tasksApi.events).toHaveBeenCalled()
    })

    await waitFor(() => {
      expect(screen.getAllByText('Pull Request Opened').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('Fix token expiry validation in auth filter')).toBeInTheDocument()
    })

    expect(screen.getByText('Recent Engineering Activity')).toBeInTheDocument()
  })

  it('renders empty state when user has no organizations', async () => {
    renderComponent({
      organizations: [],
      activeOrganization: null,
      activeOrganizationId: null,
    })

    await waitFor(() => {
      expect(screen.getByText('Welcome to DevFlow')).toBeInTheDocument()
      expect(screen.getByText(/Create your first organization to configure GitHub repositories/i)).toBeInTheDocument()
    })
  })

  it('renders error state when organization query fails', async () => {
    renderComponent({
      error: new Error('Failed to reach cluster API'),
    })

    await waitFor(() => {
      expect(screen.getByText('Could not load organizations')).toBeInTheDocument()
      expect(screen.getByText('Failed to reach cluster API')).toBeInTheDocument()
    })
  })

  it('opens New Task modal when New Task button is clicked', async () => {
    const user = userEvent.setup()
    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /New Task/i })).toBeInTheDocument()
    })

    const newTaskBtn = screen.getByRole('button', { name: /New Task/i })
    await user.click(newTaskBtn)

    expect(screen.getByText('Dispatch AI Task')).toBeInTheDocument()
    expect(screen.getByLabelText(/Task Title/i)).toBeInTheDocument()
  })
})
