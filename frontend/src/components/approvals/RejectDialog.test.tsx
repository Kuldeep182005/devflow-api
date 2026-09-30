import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RejectDialog } from './RejectDialog'
import { approvalsApi } from '@/api/approvals'
import { ApiClientError } from '@/api/client'
import type { ApprovalResponse } from '@/types/api'

vi.mock('@/api/approvals', () => ({
  approvalsApi: {
    reject: vi.fn(),
  },
}))

describe('RejectDialog', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1111'
  const mockApprovalId = 'approval-uuid-2222'
  const mockTaskId = 'task-uuid-3333'
  const mockOnClose = vi.fn()
  const mockOnSuccess = vi.fn()

  const mockRejectedResponse: ApprovalResponse = {
    id: mockApprovalId,
    proposedChangeId: 'proposal-uuid-4444',
    taskId: mockTaskId,
    repositoryId: 'repo-uuid-5555',
    organizationId: mockOrgId,
    status: 'REJECTED',
    proposedChangeVersion: 1,
    sandboxExecutionId: 'sandbox-uuid-6666',
    sandboxExecutionStatus: 'SUCCEEDED',
    requestedByUserId: 'user-requester-777',
    decidedByUserId: 'user-admin-888',
    decisionReason: 'Architecture violation: repository pattern bypassed',
    requestedAt: '2026-09-30T10:00:00Z',
    decidedAt: '2026-09-30T10:35:00Z',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:35:00Z',
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
        <RejectDialog
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

  it('renders modal dialog with terminal decision warning and mandatory reason input', () => {
    renderComponent()

    expect(screen.getByText('Reject Proposed Change')).toBeInTheDocument()
    expect(screen.getByText('Terminal Decision')).toBeInTheDocument()
    expect(screen.getByLabelText(/Rejection Reason/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Reject Proposal/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeInTheDocument()
  })

  it('validates that rejection reason is required before submitting', async () => {
    const user = userEvent.setup()
    renderComponent()

    const submitBtn = screen.getByRole('button', { name: /Reject Proposal/i })
    await user.click(submitBtn)

    expect(screen.getByText('Rejection reason is required.')).toBeInTheDocument()
    expect(approvalsApi.reject).not.toHaveBeenCalled()
  })

  it('submits valid rejection reason successfully and invokes callbacks', async () => {
    const user = userEvent.setup()
    vi.mocked(approvalsApi.reject).mockResolvedValue(mockRejectedResponse)

    renderComponent()

    const reasonInput = screen.getByLabelText(/Rejection Reason/i)
    await user.type(reasonInput, 'Architecture violation: repository pattern bypassed')

    const submitBtn = screen.getByRole('button', { name: /Reject Proposal/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(approvalsApi.reject).toHaveBeenCalledWith(mockOrgId, mockApprovalId, {
        reason: 'Architecture violation: repository pattern bypassed',
      })
      expect(mockOnSuccess).toHaveBeenCalledWith(mockRejectedResponse)
      expect(mockOnClose).toHaveBeenCalled()
    })
  })

  it('handles backend 409 conflict and refreshes authoritative state', async () => {
    const user = userEvent.setup()
    const conflictError = new ApiClientError({
      message: 'Approval already decided or state transition conflict',
      status: 409,
      code: 'APPROVAL_ALREADY_DECIDED',
      requestId: 'req-conflict-789',
    })
    vi.mocked(approvalsApi.reject).mockRejectedValue(conflictError)

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    renderComponent()

    const reasonInput = screen.getByLabelText(/Rejection Reason/i)
    await user.type(reasonInput, 'Declined due to test regression')

    const submitBtn = screen.getByRole('button', { name: /Reject Proposal/i })
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
      requestId: 'req-forbidden-999',
    })
    vi.mocked(approvalsApi.reject).mockRejectedValue(forbiddenError)

    renderComponent()

    const reasonInput = screen.getByLabelText(/Rejection Reason/i)
    await user.type(reasonInput, 'Declined due to security concern')

    const submitBtn = screen.getByRole('button', { name: /Reject Proposal/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByText('Rejection Error')).toBeInTheDocument()
      expect(
        screen.getByText(/You do not have permission to reject changes/i)
      ).toBeInTheDocument()
    })
  })
})
