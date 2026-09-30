import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApprovalDetailPage } from './ApprovalDetailPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { approvalsApi } from '@/api/approvals'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'
import type { ApprovalResponse } from '@/types/api'

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    get: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    getMutation: vi.fn(),
    execute: vi.fn(),
  },
}))

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    get: vi.fn(),
  },
}))

vi.mock('@/api/tasks', () => ({
  tasksApi: {
    get: vi.fn(),
  },
}))

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    listMembers: vi.fn(),
  },
}))

describe('ApprovalDetailPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1111'
  const mockApprovalId = 'approval-uuid-2222'
  const mockTaskId = 'task-uuid-3333'
  const mockRepoId = 'repo-uuid-4444'

  const mockOrg = {
    id: mockOrgId,
    name: 'DevFlow Test Org',
    slug: 'devflow-test-org',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockAdminAuthContext: AuthContextValue = {
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
    externalId: '999',
    fullName: 'devflow-platform/auth-service',
    defaultBranch: 'main',
    active: true,
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockTask = {
    id: mockTaskId,
    organizationId: mockOrgId,
    repositoryId: mockRepoId,
    createdByUserId: 'user-requester-777',
    sourceType: 'MANUAL' as const,
    sourceReference: null,
    title: 'Fix authentication token expiry filter',
    description: 'Resolve token validation issues',
    status: 'WAITING_FOR_APPROVAL' as const,
    branchName: 'devflow/fix-auth-token',
    currentStep: null,
    failureCode: null,
    failureMessage: null,
    failedAt: null,
    createdAt: '2026-09-30T10:50:00Z',
    updatedAt: '2026-09-30T11:00:00Z',
    startedAt: '2026-09-30T10:51:00Z',
    finishedAt: null,
  }

  const mockPendingApproval: ApprovalResponse = {
    id: mockApprovalId,
    proposedChangeId: 'proposal-uuid-5555',
    taskId: mockTaskId,
    repositoryId: mockRepoId,
    organizationId: mockOrgId,
    status: 'PENDING',
    proposedChangeVersion: 1,
    sandboxExecutionId: 'sandbox-uuid-6666',
    sandboxExecutionStatus: 'SUCCEEDED',
    requestedByUserId: 'user-requester-777',
    decidedByUserId: null,
    decisionReason: null,
    requestedAt: '2026-09-30T11:00:00Z',
    decidedAt: null,
    createdAt: '2026-09-30T11:00:00Z',
    updatedAt: '2026-09-30T11:00:00Z',
    version: 1,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })

    vi.mocked(repositoriesApi.get).mockResolvedValue(mockRepository)
    vi.mocked(tasksApi.get).mockResolvedValue(mockTask)
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

  const renderComponent = (authCtx = mockAdminAuthContext) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={authCtx}>
          <OrganizationContext.Provider value={mockOrgContext}>
            <MemoryRouter
              initialEntries={[`/organizations/${mockOrgId}/approvals/${mockApprovalId}`]}
            >
              <Routes>
                <Route
                  path="/organizations/:organizationId/approvals/:approvalId"
                  element={<ApprovalDetailPage />}
                />
              </Routes>
            </MemoryRouter>
          </OrganizationContext.Provider>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders pending approval with task title, repository, version, and sandbox status', async () => {
    vi.mocked(approvalsApi.get).mockResolvedValue(mockPendingApproval)

    renderComponent()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { level: 1, name: 'Fix authentication token expiry filter' })
      ).toBeInTheDocument()
      expect(screen.getByText('Pending Review')).toBeInTheDocument()
      expect(screen.getAllByText('Sandbox Passed')).toHaveLength(2)
      expect(screen.getAllByText('devflow-platform/auth-service')).toHaveLength(2)
      expect(screen.getByText('Version 1')).toBeInTheDocument()
      expect(screen.getByText('DevFlow Security Gate')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Approve Change/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Reject Proposal/i })).toBeInTheDocument()
    })
  })

  it('renders file diff availability notice indicating diff is not exposed by current API', async () => {
    vi.mocked(approvalsApi.get).mockResolvedValue(mockPendingApproval)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('File Diff Availability')).toBeInTheDocument()
      expect(
        screen.getByText(/Detailed line-level diffs are not exposed directly through the public approval API/i)
      ).toBeInTheDocument()
    })
  })

  it('disables Approve button and displays warning when sandbox verification is not SUCCEEDED', async () => {
    const failedSandboxApproval: ApprovalResponse = {
      ...mockPendingApproval,
      sandboxExecutionStatus: 'FAILED',
    }
    vi.mocked(approvalsApi.get).mockResolvedValue(failedSandboxApproval)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Sandbox Verification Incomplete or Failed')).toBeInTheDocument()
      const approveBtn = screen.getByRole('button', { name: /Approve Change/i })
      expect(approveBtn).toBeDisabled()
    })
  })

  it('disables decision buttons and displays role notice for non-decider (viewer/member)', async () => {
    vi.mocked(approvalsApi.get).mockResolvedValue(mockPendingApproval)

    // Mock member as VIEWER
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [
        {
          organizationId: mockOrgId,
          userId: 'user-viewer-222',
          email: 'viewer@devflow.local',
          displayName: 'Viewer User',
          role: 'VIEWER',
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

    const viewerAuth: AuthContextValue = {
      session: {
        accessToken: 'token_viewer',
        tokenType: 'Bearer',
        expiresAt: Date.now() + 3600000,
        userId: 'user-viewer-222',
        email: 'viewer@devflow.local',
        displayName: 'Viewer User',
      },
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    }

    renderComponent(viewerAuth)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Approve Change/i })).toBeDisabled()
      expect(screen.getByRole('button', { name: /Reject Proposal/i })).toBeDisabled()
      expect(
        screen.getByText(/is read-only for change authorization/i)
      ).toBeInTheDocument()
    })
  })

  it('renders approved state with recorded review decision and read-only mutation status', async () => {
    const approvedRecord: ApprovalResponse = {
      ...mockPendingApproval,
      status: 'APPROVED',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Code is clean and verified',
      decidedAt: '2026-09-30T11:30:00Z',
    }
    vi.mocked(approvalsApi.get).mockResolvedValue(approvedRecord)
    vi.mocked(approvalsApi.getMutation).mockResolvedValue({
      id: 'mutation-uuid-777',
      approvalId: mockApprovalId,
      taskId: mockTaskId,
      repositoryId: mockRepoId,
      organizationId: mockOrgId,
      proposedChangeId: 'proposal-uuid-5555',
      proposedChangeVersion: 1,
      sandboxExecutionId: 'sandbox-uuid-6666',
      baseRevision: 'main',
      branchName: 'devflow/fix-auth-token',
      commitSha: 'a1b2c3d4e5f6',
      pullRequestNumber: 42,
      pullRequestUrl: 'https://github.com/devflow-platform/auth-service/pull/42',
      status: 'SUCCEEDED',
      failureCode: null,
      failureMessage: null,
      createdAt: '2026-09-30T11:30:00Z',
      updatedAt: '2026-09-30T11:31:00Z',
      version: 1,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getAllByText('Approved')).toHaveLength(2)
      expect(screen.getByText('Recorded Review Decision')).toBeInTheDocument()
      expect(screen.getByText('Code is clean and verified')).toBeInTheDocument()
      expect(screen.getByText('GitHub Remote Mutation')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /PR #42/i })).toBeInTheDocument()
      expect(screen.getByText('a1b2c3d')).toBeInTheDocument()
      // Decision buttons should not be present in terminal approved state
      expect(screen.queryByRole('button', { name: /Approve Change/i })).toBeNull()
      expect(screen.queryByRole('button', { name: /Reject Proposal/i })).toBeNull()
    })
  })

  it('renders approved state without mutation yet and displays Ready for GitHub Remote Delivery card', async () => {
    const approvedRecord: ApprovalResponse = {
      ...mockPendingApproval,
      status: 'APPROVED',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Ready for GitHub merge',
      decidedAt: '2026-09-30T11:20:00Z',
    }
    vi.mocked(approvalsApi.get).mockResolvedValue(approvedRecord)
    vi.mocked(approvalsApi.getMutation).mockResolvedValue(null as any)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Ready for GitHub Remote Delivery')).toBeInTheDocument()
      expect(screen.getByText('Approved & Awaiting Execution')).toBeInTheDocument()
      expect(screen.getAllByRole('button', { name: /Execute/i }).length).toBeGreaterThanOrEqual(1)
    })
  })

  it('allows authorized user to open Execute dialog from header or delivery card', async () => {
    const user = userEvent.setup()
    const approvedRecord: ApprovalResponse = {
      ...mockPendingApproval,
      status: 'APPROVED',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Ready for GitHub merge',
      decidedAt: '2026-09-30T11:20:00Z',
    }
    vi.mocked(approvalsApi.get).mockResolvedValue(approvedRecord)
    vi.mocked(approvalsApi.getMutation).mockResolvedValue(null as any)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Ready for GitHub Remote Delivery')).toBeInTheDocument()
    })

    const executeBtn = screen.getByRole('button', { name: /Execute GitHub Mutation/i })
    await user.click(executeBtn)

    expect(screen.getByText('Execute GitHub Change')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Confirm & Execute on GitHub/i })).toBeInTheDocument()
  })

  it('disables execute action and shows read-only notice for non-decider (VIEWER)', async () => {
    const approvedRecord: ApprovalResponse = {
      ...mockPendingApproval,
      status: 'APPROVED',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Ready for GitHub merge',
      decidedAt: '2026-09-30T11:20:00Z',
    }
    vi.mocked(approvalsApi.get).mockResolvedValue(approvedRecord)
    vi.mocked(approvalsApi.getMutation).mockResolvedValue(null as any)
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [
        {
          organizationId: mockOrgId,
          userId: 'user-admin-111',
          email: 'admin@devflow.local',
          displayName: 'Admin User',
          role: 'VIEWER',
          createdAt: '2026-09-30T10:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      page: 0,
      first: true,
      last: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText(/Your role \(VIEWER\) is read-only/i)).toBeInTheDocument()
      const headerExecuteBtn = screen.getByRole('button', { name: /Execute on GitHub/i })
      expect(headerExecuteBtn).toBeDisabled()
    })
  })

  it('renders rejected state with recorded rejection reason', async () => {
    const rejectedRecord: ApprovalResponse = {
      ...mockPendingApproval,
      status: 'REJECTED',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Violates architecture guidelines',
      decidedAt: '2026-09-30T11:45:00Z',
    }
    vi.mocked(approvalsApi.get).mockResolvedValue(rejectedRecord)

    renderComponent()

    await waitFor(() => {
      expect(screen.getAllByText('Rejected')).toHaveLength(2)
      expect(screen.getByText('Recorded Review Decision')).toBeInTheDocument()
      expect(screen.getByText('Violates architecture guidelines')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /Approve Change/i })).toBeNull()
    })
  })

  it('opens Approve modal when Approve Change is clicked', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.get).mockResolvedValue(mockPendingApproval)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Approve Change/i })).toBeInTheDocument()
    })

    const approveBtn = screen.getByRole('button', { name: /Approve Change/i })
    await user.click(approveBtn)

    expect(screen.getByText('Consequential Mutation Authorization')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Approve & Authorize/i })).toBeInTheDocument()
  })

  it('opens Reject modal when Reject Proposal is clicked', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.get).mockResolvedValue(mockPendingApproval)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Reject Proposal/i })).toBeInTheDocument()
    })

    const rejectBtn = screen.getByRole('button', { name: /Reject Proposal/i })
    await user.click(rejectBtn)

    expect(screen.getByText('Terminal Decision')).toBeInTheDocument()
    expect(screen.getByLabelText(/Rejection Reason/i)).toBeInTheDocument()
  })

  it('renders 404 error when approval is not found', async () => {
    const notFoundError = new ApiClientError({
      message: 'Approval not found',
      status: 404,
      code: 'APPROVAL_NOT_FOUND',
      requestId: 'req-404-111',
    })
    vi.mocked(approvalsApi.get).mockRejectedValue(notFoundError)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Approval Request Not Found')).toBeInTheDocument()
      expect(
        screen.getByText(/The requested approval record does not exist or you do not have permission/i)
      ).toBeInTheDocument()
    })
  })
})
