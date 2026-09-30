import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApproveDialog } from './ApproveDialog'
import { approvalsApi } from '@/api/approvals'
import { ApiClientError } from '@/api/client'
import type { ApprovalResponse } from '@/types/api'

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    approve: vi.fn(),
  },
}))

describe('ApproveDialog', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1111'
  const mockApprovalId = 'approval-uuid-2222'
  const mockTaskId = 'task-uuid-3333'
  const mockOnClose = vi.fn()
  const mockOnSuccess = vi.fn()

  const mockApprovedResponse: ApprovalResponse = {
    id: mockApprovalId,
    proposedChangeId: 'proposal-uuid-4444',
    taskId: mockTaskId,
    repositoryId: 'repo-uuid-5555',
    organizationId: mockOrgId,
    status: 'APPROVED',
    proposedChangeVersion: 1,
    sandboxExecutionId: 'sandbox-uuid-6666',
    sandboxExecutionStatus: 'SUCCEEDED',
    requestedByUserId: 'user-requester-777',
    decidedByUserId: 'user-admin-888',
    decisionReason: 'Looks good, tests pass',
    requestedAt: '2026-09-30T10:00:00Z',
    decidedAt: '2026-09-30T10:30:00Z',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:30:00Z',
    version: 2,
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
        <ApproveDialog
          isOpen={isOpen}
          onClose={mockOnClose}
          organizationId={mockOrgId}
          approvalId={mockApprovalId}
          taskId={mockTaskId}
          onSuccess={mockOnSuccess}
        />
      </QueryClientProvider>
    )
  }

  it('renders modal dialog with consequential mutation warning and review comment input', () => {
    renderComponent()

    expect(screen.getByText('Approve Proposed Change')).toBeInTheDocument()
    expect(screen.getByText('Consequential Mutation Authorization')).toBeInTheDocument()
    expect(
      screen.getByText(/By approving this proposal, you authorize the platform/i)
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/Review Comment/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Approve & Authorize/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeInTheDocument()
  })

  it('submits valid approval with review comment successfully', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.approve).mockResolvedValue(mockApprovedResponse)

    renderComponent()

    const commentInput = screen.getByLabelText(/Review Comment/i)
    await user.type(commentInput, 'Verified clean changes in sandbox')

    const submitBtn = screen.getByRole('button', { name: /Approve & Authorize/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(approvalsApi.approve).toHaveBeenCalledWith(mockOrgId, mockApprovalId, {
        comment: 'Verified clean changes in sandbox',
      })
      expect(mockOnSuccess).toHaveBeenCalledWith(mockApprovedResponse)
      expect(mockOnClose).toHaveBeenCalled()
    })
  })

  it('submits approval without comment when left empty', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.approve).mockResolvedValue(mockApprovedResponse)

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Approve & Authorize/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(approvalsApi.approve).toHaveBeenCalledWith(mockOrgId, mockApprovalId, {
        comment: undefined,
      })
    })
  })

  it('disables submit button and shows loading state during submission to prevent duplicates', async () => {
    const user = userEvent.setup()
    let resolveMutation: (val: ApprovalResponse) => void = () => {}
    const pendingPromise = new Promise<ApprovalResponse>((resolve) => {
      resolveMutation = resolve
    })
    vi.mocked(approvalsApi.approve).mockReturnValue(pendingPromise)

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Approve & Authorize/i })
    await user.click(submitBtn)

    // Button should be disabled during pending mutation
    expect(submitBtn).toBeDisabled()
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeDisabled()

    // Resolve mutation
    resolveMutation(mockApprovedResponse)
    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled()
    })
  })

  it('handles backend 409 conflict and refreshes authoritative state', async () => {
    const user = userEvent.setup()
    const conflictError = new ApiClientError({
      message: 'Approval already decided or state transition conflict',
      status: 409,
      code: 'APPROVAL_ALREADY_DECIDED',
      requestId: 'req-conflict-123',
    })
    vi.mocked(approvalsApi.approve).mockRejectedValue(conflictError)

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Approve & Authorize/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByText('Approval Conflict')).toBeInTheDocument()
      expect(
        screen.getByText(/This approval request has already been decided or superseded/i)
      ).toBeInTheDocument()
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ['organizations', mockOrgId, 'approvals', mockApprovalId],
      })
    })
  })

  it('handles backend 403 forbidden error when user lacks decider permissions', async () => {
    const user = userEvent.setup()
    const forbiddenError = new ApiClientError({
      message: 'Only organization owners and admins can approve or reject changes',
      status: 403,
      code: 'FORBIDDEN',
      requestId: 'req-forbidden-456',
    })
    vi.mocked(approvalsApi.approve).mockRejectedValue(forbiddenError)

    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Approve & Authorize/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByText('Approval Error')).toBeInTheDocument()
      expect(
        screen.getByText(/You do not have permission to approve changes/i)
      ).toBeInTheDocument()
    })
  })
})
