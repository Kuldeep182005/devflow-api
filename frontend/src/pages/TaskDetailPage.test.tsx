import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TaskDetailPage } from './TaskDetailPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { tasksApi } from '@/api/tasks'
import { repositoriesApi } from '@/api/repositories'
import { approvalsApi } from '@/api/approvals'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/tasks', () => ({
  tasksApi: {
    get: vi.fn(),
    events: vi.fn(),
    cancel: vi.fn(),
  },
}))

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    get: vi.fn(),
  },
}))

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    list: vi.fn(),
    getMutation: vi.fn(),
  },
}))

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    listMembers: vi.fn(),
  },
}))

describe('TaskDetailPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1234'
  const mockTaskId = 'task-uuid-5678'
  const mockRepoId = 'repo-uuid-9999'

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

  const mockRepository = {
    id: mockRepoId,
    organizationId: mockOrgId,
    provider: 'GITHUB' as const,
    externalId: '111222',
    fullName: 'devflow-infra/core-api',
    defaultBranch: 'main',
    active: true,
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockRunningTask = {
    id: mockTaskId,
    organizationId: mockOrgId,
    repositoryId: mockRepoId,
    createdByUserId: 'user-admin-111',
    sourceType: 'MANUAL' as const,
    sourceReference: null,
    title: 'Fix authorization token validation',
    description: 'Resolve token edge case with refresh logic in filter',
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
  }

  const mockEvents = {
    content: [
      {
        id: 'event-1',
        taskId: mockTaskId,
        organizationId: mockOrgId,
        eventType: 'TASK_CREATED' as const,
        previousState: null,
        newState: 'QUEUED' as const,
        actorSource: 'USER' as const,
        actorUserId: 'user-admin-111',
        occurredAt: '2026-09-30T11:00:00Z',
        correlationId: 'corr-111',
      },
      {
        id: 'event-2',
        taskId: mockTaskId,
        organizationId: mockOrgId,
        eventType: 'TASK_STARTED' as const,
        previousState: 'QUEUED' as const,
        newState: 'RUNNING' as const,
        actorSource: 'WORKER' as const,
        actorUserId: null,
        occurredAt: '2026-09-30T11:00:05Z',
        correlationId: 'corr-222',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    size: 100,
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

    vi.mocked(repositoriesApi.get).mockResolvedValue(mockRepository)
    vi.mocked(tasksApi.events).mockResolvedValue(mockEvents)
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      page: 0,
      first: true,
      last: true,
    })
    vi.mocked(approvalsApi.getMutation).mockResolvedValue(null as any)
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

  const renderComponent = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={mockAuthContext}>
          <OrganizationContext.Provider value={mockOrgContext}>
            <MemoryRouter initialEntries={[`/organizations/${mockOrgId}/tasks/${mockTaskId}`]}>
              <Routes>
                <Route
                  path="/organizations/:organizationId/tasks/:taskId"
                  element={<TaskDetailPage />}
                />
              </Routes>
            </MemoryRouter>
          </OrganizationContext.Provider>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders loading state initially', () => {
    vi.mocked(tasksApi.get).mockReturnValue(new Promise(() => {}))
    renderComponent()
    expect(screen.getByText(/Loading task details from backend/i)).toBeInTheDocument()
  })

  it('renders error state when task is not found (404)', async () => {
    const error404 = new ApiClientError({
      message: 'Not Found',
      status: 404,
      code: 'TASK_NOT_FOUND',
      requestId: 'req-404',
    })
    vi.mocked(tasksApi.get).mockRejectedValue(error404)
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Task Not Found')).toBeInTheDocument()
    })
  })

  it('renders task details, status, and event timeline successfully', async () => {
    vi.mocked(tasksApi.get).mockResolvedValue(mockRunningTask)
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Fix authorization token validation')).toBeInTheDocument()
      expect(screen.getByText('devflow-infra/core-api')).toBeInTheDocument()
    })

    // Check status & active live sync
    expect(screen.getAllByText('Running').length).toBeGreaterThan(0)
    expect(screen.getByText(/Live Sync \(3s\)/i)).toBeInTheDocument()

    // Check lifecycle component
    expect(screen.getByText('Task Execution Lifecycle')).toBeInTheDocument()

    // Check timeline events
    expect(screen.getByText('Audit Event Timeline')).toBeInTheDocument()
    expect(screen.getByText('Task Created')).toBeInTheDocument()
    expect(screen.getByText('Agent Started')).toBeInTheDocument()
  })

  it('renders waiting for approval banner and review action', async () => {
    const waitingTask = {
      ...mockRunningTask,
      status: 'WAITING_FOR_APPROVAL' as const,
    }
    const mockApproval = {
      id: 'approval-uuid-1',
      proposedChangeId: 'prop-uuid-1',
      taskId: mockTaskId,
      repositoryId: mockRepoId,
      organizationId: mockOrgId,
      status: 'PENDING' as const,
      proposedChangeVersion: 1,
      sandboxExecutionId: 'sandbox-exec-1',
      sandboxExecutionStatus: 'SUCCEEDED' as const,
      requestedByUserId: 'user-admin-111',
      decidedByUserId: null,
      decisionReason: null,
      requestedAt: '2026-09-30T11:05:00Z',
      decidedAt: null,
      createdAt: '2026-09-30T11:05:00Z',
      updatedAt: '2026-09-30T11:05:00Z',
      version: 1,
    }

    vi.mocked(tasksApi.get).mockResolvedValue(waitingTask)
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: [mockApproval],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      page: 0,
      first: true,
      last: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Human Approval Required')).toBeInTheDocument()
      expect(screen.getByText('Review Approval')).toBeInTheDocument()
      expect(screen.getByText('Version 1')).toBeInTheDocument()
      expect(screen.getAllByText('SUCCEEDED').length).toBeGreaterThan(0)
    })
  })

  it('renders failure message card when task has failed', async () => {
    const failedTask = {
      ...mockRunningTask,
      status: 'FAILED' as const,
      failureCode: 'SANDBOX_COMPILATION_ERROR',
      failureMessage: 'Compilation failed with 2 error(s) in AuthFilter.java',
      failedAt: '2026-09-30T11:08:00Z',
    }

    vi.mocked(tasksApi.get).mockResolvedValue(failedTask)
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Task Execution Failed')).toBeInTheDocument()
      expect(screen.getByText('SANDBOX_COMPILATION_ERROR')).toBeInTheDocument()
      expect(screen.getByText(/Compilation failed with 2 error\(s\)/i)).toBeInTheDocument()
    })
  })

  it('cancels active task when confirmed', async () => {
    const user = userEvent.setup()
    vi.mocked(tasksApi.get).mockResolvedValue(mockRunningTask)
    const cancelledTask = {
      ...mockRunningTask,
      status: 'CANCELLED' as const,
    }
    vi.mocked(tasksApi.cancel).mockResolvedValue(cancelledTask)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Cancel Task/i })).toBeInTheDocument()
    })

    // Click cancel button to open confirmation dialog
    await user.click(screen.getByRole('button', { name: /Cancel Task/i }))

    expect(screen.getByText('Confirm Task Cancellation')).toBeInTheDocument()
    expect(screen.getByText('Irreversible Action')).toBeInTheDocument()

    // Confirm cancellation
    const confirmBtn = screen.getByRole('button', { name: /Confirm Cancellation/i })
    await user.click(confirmBtn)

    await waitFor(() => {
      expect(tasksApi.cancel).toHaveBeenCalledWith(
        mockOrgId,
        mockTaskId,
        expect.stringMatching(/^cancel-task-/)
      )
    })
  })

  it('does not display cancel button when task is already in terminal state', async () => {
    const terminalTask = {
      ...mockRunningTask,
      status: 'SUCCEEDED' as const,
    }
    vi.mocked(tasksApi.get).mockResolvedValue(terminalTask)
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Pipeline Completed Successfully')).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: /Cancel Task/i })).toBeNull()
  })

  it('renders "Human Approval Required" and "Review Approval" link when task is in WAITING_FOR_APPROVAL status', async () => {
    const waitingTask = {
      ...mockRunningTask,
      status: 'WAITING_FOR_APPROVAL' as const,
    }
    const mockApproval = {
      id: 'approval-uuid-8888',
      proposedChangeId: 'proposal-1',
      taskId: mockTaskId,
      repositoryId: mockRepoId,
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
    }

    vi.mocked(tasksApi.get).mockResolvedValue(waitingTask)
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: [mockApproval],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      page: 0,
      first: true,
      last: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Human Approval Required')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Review Approval/i })).toBeInTheDocument()
    })
  })

  it('displays authoritative GitHub mutation details and safe PR link when mutation exists for approved task', async () => {
    const approvedTask = {
      ...mockRunningTask,
      status: 'SUCCEEDED' as const,
    }
    const mockApprovedApproval = {
      id: 'approval-1',
      proposedChangeId: 'proposal-1',
      taskId: mockTaskId,
      repositoryId: mockRepoId,
      organizationId: mockOrgId,
      status: 'APPROVED' as const,
      proposedChangeVersion: 1,
      sandboxExecutionId: 'sandbox-1',
      sandboxExecutionStatus: 'SUCCEEDED' as const,
      requestedByUserId: 'user-admin-111',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Approved for production deploy',
      requestedAt: '2026-09-30T11:05:00Z',
      decidedAt: '2026-09-30T11:10:00Z',
      createdAt: '2026-09-30T11:05:00Z',
      updatedAt: '2026-09-30T11:10:00Z',
      version: 2,
    }
    const mockMutation = {
      id: 'mutation-1',
      organizationId: mockOrgId,
      repositoryId: mockRepoId,
      taskId: mockTaskId,
      proposedChangeId: 'proposal-1',
      proposedChangeVersion: 1,
      approvalId: 'approval-1',
      sandboxExecutionId: 'sandbox-1',
      baseRevision: 'main',
      branchName: 'devflow/task-1/change-1',
      commitSha: 'c0ffee123456',
      pullRequestNumber: 99,
      pullRequestUrl: 'https://github.com/devflow-infra/core-api/pull/99',
      status: 'SUCCEEDED' as const,
      failureCode: null,
      failureMessage: null,
      createdAt: '2026-09-30T11:12:00Z',
      updatedAt: '2026-09-30T11:15:00Z',
      version: 1,
    }

    vi.mocked(tasksApi.get).mockResolvedValue(approvedTask)
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: [mockApprovedApproval],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      page: 0,
      first: true,
      last: true,
    })
    vi.mocked(approvalsApi.getMutation).mockResolvedValue(mockMutation)

    renderComponent()

    await waitFor(() => {
      expect(screen.getAllByText('Succeeded').length).toBeGreaterThanOrEqual(2)
      expect(screen.getByText('c0ffee1')).toBeInTheDocument()
      const prLinks = screen.getAllByRole('link', { name: /PR #99/i })
      expect(prLinks.length).toBeGreaterThanOrEqual(1)
      expect(prLinks[0]).toHaveAttribute('href', 'https://github.com/devflow-infra/core-api/pull/99')
      expect(prLinks[0]).toHaveAttribute('target', '_blank')
      expect(prLinks[0]).toHaveAttribute('rel', 'noopener noreferrer')
      expect(screen.getByText('View Approval & Delivery')).toBeInTheDocument()
    })
  })

  it('does not display GitHub mutation details when no authoritative mutation exists', async () => {
    vi.mocked(tasksApi.get).mockResolvedValue(mockRunningTask)
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      page: 0,
      first: true,
      last: true,
    })
    vi.mocked(approvalsApi.getMutation).mockResolvedValue(null as any)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Fix authorization token validation')).toBeInTheDocument()
    })

    // Succeeded mutation status and PR links should not exist
    expect(screen.queryByText('Succeeded')).toBeNull()
    expect(screen.queryByRole('link', { name: /PR #/i })).toBeNull()
  })
})

