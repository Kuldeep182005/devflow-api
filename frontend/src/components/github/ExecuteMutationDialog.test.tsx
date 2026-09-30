import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ExecuteMutationDialog } from './ExecuteMutationDialog'
import { approvalsApi } from '@/api/approvals'
import { ApiClientError } from '@/api/client'
import type { GithubMutationResponse } from '@/types/api'

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    execute: vi.fn(),
  },
}))

describe('ExecuteMutationDialog', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1111'
  const mockApprovalId = 'approval-uuid-2222'
  const mockTaskId = 'task-uuid-3333'
  const mockOnClose = vi.fn()
  const mockOnSuccess = vi.fn()

  const mockMutationResponse: GithubMutationResponse = {
    id: 'mutation-uuid-4444',
    organizationId: mockOrgId,
    repositoryId: 'repo-uuid-5555',
    taskId: mockTaskId,
    proposedChangeId: 'proposal-uuid-6666',
    proposedChangeVersion: 1,
    approvalId: mockApprovalId,
    sandboxExecutionId: 'sandbox-uuid-7777',
    baseRevision: 'main',
    branchName: 'devflow/task-3333/change-6666',
    commitSha: null,
    pullRequestNumber: null,
    pullRequestUrl: null,
    status: 'QUEUED',
    failureCode: null,
    failureMessage: null,
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
    version: 1,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })
  })

  const renderComponent = (isOpen = true) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <ExecuteMutationDialog
          isOpen={isOpen}
          onClose={mockOnClose}
          organizationId={mockOrgId}
          approvalId={mockApprovalId}
          taskId={mockTaskId}
          repositoryFullName="devflow-org/core-service"
          branchName="devflow/task-3333/change-6666"
          onSuccess={mockOnSuccess}
        />
      </QueryClientProvider>
    )
  }

  it('renders modal dialog with consequential notice and repository metadata', () => {
    renderComponent()

    expect(screen.getByText('Execute GitHub Change')).toBeInTheDocument()
    expect(screen.getByText('Remote GitHub Mutation Notice')).toBeInTheDocument()
    expect(
      screen.getByText(/Executing this approved change will cause consequential GitHub-side changes/i)
    ).toBeInTheDocument()
    expect(screen.getByText('devflow-org/core-service')).toBeInTheDocument()
    expect(screen.getByText('devflow/task-3333/change-6666')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /Confirm & Execute on GitHub/i })
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeInTheDocument()
  })

  it('submits mutation execution successfully and invokes callbacks', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.execute).mockResolvedValue(mockMutationResponse)

    renderComponent()

    const confirmBtn = screen.getByRole('button', { name: /Confirm & Execute on GitHub/i })
    await user.click(confirmBtn)

    await waitFor(() => {
      expect(approvalsApi.execute).toHaveBeenCalledWith(mockOrgId, mockApprovalId)
      expect(mockOnSuccess).toHaveBeenCalledWith(mockMutationResponse)
      expect(mockOnClose).toHaveBeenCalled()
    })
  })

  it('disables submit button and shows loading state during execution to prevent duplicates', async () => {
    const user = userEvent.setup()
    let resolveMutation: (val: GithubMutationResponse) => void = () => {}
    const pendingPromise = new Promise<GithubMutationResponse>((resolve) => {
      resolveMutation = resolve
    })
    vi.mocked(approvalsApi.execute).mockReturnValue(pendingPromise)

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Confirm & Execute on GitHub/i })
    await user.click(submitBtn)

    expect(submitBtn).toBeDisabled()
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeDisabled()

    resolveMutation(mockMutationResponse)
    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled()
    })
  })

  it('handles backend 409 conflict and refreshes authoritative state without blind retry', async () => {
    const user = userEvent.setup()
    const conflictError = new ApiClientError({
      message: 'Base branch revision is stale or installation not found',
      status: 409,
      code: 'BASE_REVISION_STALE',
      requestId: 'req-conflict-123',
    })
    vi.mocked(approvalsApi.execute).mockRejectedValue(conflictError)

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Confirm & Execute on GitHub/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByText('Execution Conflict')).toBeInTheDocument()
      expect(
        screen.getByText(/Base branch revision is stale or installation not found/i)
      ).toBeInTheDocument()
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ['organizations', mockOrgId, 'approvals', mockApprovalId],
      })
    })
  })

  it('handles backend 403 forbidden error when user lacks decider permissions', async () => {
    const user = userEvent.setup()
    const forbiddenError = new ApiClientError({
      message: 'Only organization owners and admins can execute mutations on GitHub.',
      status: 403,
      code: 'FORBIDDEN',
      requestId: 'req-forbidden-456',
    })
    vi.mocked(approvalsApi.execute).mockRejectedValue(forbiddenError)

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Confirm & Execute on GitHub/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByText('Execution Error')).toBeInTheDocument()
      expect(
        screen.getByText(/Only organization owners and admins can trigger execution/i)
      ).toBeInTheDocument()
    })
  })
})
