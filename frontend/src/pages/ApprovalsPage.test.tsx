import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApprovalsPage } from './ApprovalsPage'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { approvalsApi } from '@/api/approvals'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import type { ApprovalResponse } from '@/types/api'

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/tasks', () => ({
  tasksApi: {
    list: vi.fn(),
  },
}))

describe('ApprovalsPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1111'

  const mockOrg = {
    id: mockOrgId,
    name: 'DevFlow Platform Org',
    slug: 'devflow-platform-org',
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

  const mockApprovals: ApprovalResponse[] = [
    {
      id: 'approval-111',
      proposedChangeId: 'prop-1',
      taskId: 'task-1',
      repositoryId: 'repo-1',
      organizationId: mockOrgId,
      status: 'PENDING',
      proposedChangeVersion: 1,
      sandboxExecutionId: 'sandbox-1',
      sandboxExecutionStatus: 'SUCCEEDED',
      requestedByUserId: 'user-1',
      decidedByUserId: null,
      decisionReason: null,
      requestedAt: '2026-09-30T11:00:00Z',
      decidedAt: null,
      createdAt: '2026-09-30T11:00:00Z',
      updatedAt: '2026-09-30T11:00:00Z',
      version: 1,
    },
    {
      id: 'approval-222',
      proposedChangeId: 'prop-2',
      taskId: 'task-2',
      repositoryId: 'repo-1',
      organizationId: mockOrgId,
      status: 'APPROVED',
      proposedChangeVersion: 2,
      sandboxExecutionId: 'sandbox-2',
      sandboxExecutionStatus: 'SUCCEEDED',
      requestedByUserId: 'user-1',
      decidedByUserId: 'user-admin-111',
      decisionReason: 'Verified patch',
      requestedAt: '2026-09-30T09:00:00Z',
      decidedAt: '2026-09-30T09:30:00Z',
      createdAt: '2026-09-30T09:00:00Z',
      updatedAt: '2026-09-30T09:30:00Z',
      version: 2,
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
      content: [
        {
          id: 'repo-1',
          organizationId: mockOrgId,
          provider: 'GITHUB',
          externalId: '111',
          fullName: 'devflow/core-api',
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
    })

    vi.mocked(tasksApi.list).mockResolvedValue({
      content: [
        {
          id: 'task-1',
          organizationId: mockOrgId,
          repositoryId: 'repo-1',
          createdByUserId: 'user-1',
          sourceType: 'MANUAL',
          sourceReference: null,
          title: 'Fix JWT Token Validation',
          description: 'Ensure token expiration is checked properly',
          status: 'WAITING_FOR_APPROVAL',
          branchName: 'devflow/fix-jwt',
          currentStep: null,
          failureCode: null,
          failureMessage: null,
          failedAt: null,
          createdAt: '2026-09-30T10:50:00Z',
          updatedAt: '2026-09-30T11:00:00Z',
          startedAt: '2026-09-30T10:51:00Z',
          finishedAt: null,
        },
        {
          id: 'task-2',
          organizationId: mockOrgId,
          repositoryId: 'repo-1',
          createdByUserId: 'user-1',
          sourceType: 'MANUAL',
          sourceReference: null,
          title: 'Add database health check endpoint',
          description: null,
          status: 'SUCCEEDED',
          branchName: 'devflow/health-check',
          currentStep: null,
          failureCode: null,
          failureMessage: null,
          failedAt: null,
          createdAt: '2026-09-30T08:50:00Z',
          updatedAt: '2026-09-30T09:30:00Z',
          startedAt: '2026-09-30T08:51:00Z',
          finishedAt: '2026-09-30T09:30:00Z',
        },
      ],
      totalElements: 2,
      totalPages: 1,
      size: 100,
      page: 0,
      first: true,
      last: true,
    })
  })

  const renderComponent = (initialPath = `/organizations/${mockOrgId}/approvals`) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={mockAuthContext}>
          <OrganizationContext.Provider value={mockOrgContext}>
            <MemoryRouter initialEntries={[initialPath]}>
              <Routes>
                <Route
                  path="/organizations/:organizationId/approvals"
                  element={<ApprovalsPage />}
                />
              </Routes>
            </MemoryRouter>
          </OrganizationContext.Provider>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders loading state initially', () => {
    vi.mocked(approvalsApi.list).mockReturnValue(new Promise(() => {}))
    renderComponent()

    expect(screen.getByText(/Loading approval queue from backend/i)).toBeInTheDocument()
  })

  it('renders list of approvals with task titles, repo names, statuses, and sandbox checks', async () => {
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: mockApprovals,
      totalElements: 2,
      totalPages: 1,
      size: 15,
      page: 0,
      first: true,
      last: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Human Approval Queue')).toBeInTheDocument()
      expect(screen.getByText('Fix JWT Token Validation')).toBeInTheDocument()
      expect(screen.getByText('Add database health check endpoint')).toBeInTheDocument()
      expect(screen.getAllByText('devflow/core-api')).toHaveLength(2)
      expect(screen.getAllByText('Pending Review').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('Approved')).toBeInTheDocument()
      expect(screen.getAllByText('Sandbox Passed')).toHaveLength(2)
    })
  })

  it('filters approvals by status tabs (PENDING vs DECIDED)', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: mockApprovals,
      totalElements: 2,
      totalPages: 1,
      size: 15,
      page: 0,
      first: true,
      last: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Fix JWT Token Validation')).toBeInTheDocument()
      expect(screen.getByText('Add database health check endpoint')).toBeInTheDocument()
    })

    // Click Pending Review tab
    const pendingTab = screen.getByRole('button', { name: /Pending Review/i })
    await user.click(pendingTab)

    expect(screen.getByText('Fix JWT Token Validation')).toBeInTheDocument()
    expect(screen.queryByText('Add database health check endpoint')).toBeNull()

    // Click Decided tab
    const decidedTab = screen.getByRole('button', { name: /Decided/i })
    await user.click(decidedTab)

    expect(screen.queryByText('Fix JWT Token Validation')).toBeNull()
    expect(screen.getByText('Add database health check endpoint')).toBeInTheDocument()
  })

  it('renders empty state when no approvals exist', async () => {
    vi.mocked(approvalsApi.list).mockResolvedValue({
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
      expect(screen.getByText('No Approvals Found')).toBeInTheDocument()
      expect(
        screen.getByText(/When AI agents complete task analysis and sandbox verification/i)
      ).toBeInTheDocument()
    })
  })

  it('renders error message and allows retry on API error', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.list).mockRejectedValueOnce(new Error('Backend connection timeout'))

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Failed to Load Approvals')).toBeInTheDocument()
      expect(screen.getByText('Backend connection timeout')).toBeInTheDocument()
    })

    vi.mocked(approvalsApi.list).mockResolvedValueOnce({
      content: mockApprovals,
      totalElements: 2,
      totalPages: 1,
      size: 15,
      page: 0,
      first: true,
      last: true,
    })

    const retryBtn = screen.getByRole('button', { name: /Retry/i })
    await user.click(retryBtn)

    await waitFor(() => {
      expect(screen.getByText('Fix JWT Token Validation')).toBeInTheDocument()
    })
  })

  it('handles pagination navigation when multiple pages exist', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.list).mockResolvedValue({
      content: [mockApprovals[0]],
      totalElements: 20,
      totalPages: 2,
      size: 15,
      page: 0,
      first: true,
      last: false,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText(/Showing page/i)).toBeInTheDocument()
      expect(screen.getByText(/total records/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Next/i })).toBeEnabled()
      expect(screen.getByRole('button', { name: /Previous/i })).toBeDisabled()
    })

    const nextBtn = screen.getByRole('button', { name: /Next/i })
    await user.click(nextBtn)

    await waitFor(() => {
      expect(approvalsApi.list).toHaveBeenCalledWith(
        mockOrgId,
        expect.objectContaining({ page: 1 })
      )
    })
  })
})
