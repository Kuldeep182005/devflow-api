import React, { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { XOctagon, AlertTriangle } from 'lucide-react'
import { Dialog } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { approvalsApi } from '@/api/approvals'
import { ApiClientError } from '@/api/client'
import type { ApprovalResponse, RejectApprovalRequest } from '@/types/api'

interface RejectDialogProps {
  isOpen: boolean
  onClose: () => void
  organizationId: string
  approvalId: string
  taskId: string
  onSuccess?: (updatedApproval: ApprovalResponse) => void
}

export const RejectDialog: React.FC<RejectDialogProps> = ({
  isOpen,
  onClose,
  organizationId,
  approvalId,
  taskId,
  onSuccess,
}) => {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [error, setError] = useState<{
    message: string
    code?: string
    requestId?: string
    isConflict?: boolean
  } | null>(null)

  const handleClose = () => {
    setReason('')
    setFieldError('')
    setError(null)
    onClose()
  }

  const rejectMutation = useMutation({
    mutationFn: (payload: RejectApprovalRequest) =>
      approvalsApi.reject(organizationId, approvalId, payload),
    onSuccess: (updatedApproval) => {
      queryClient.setQueryData(
        ['organizations', organizationId, 'approvals', approvalId],
        updatedApproval
      )
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'approvals'],
      })
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks', taskId],
      })
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks', taskId, 'events'],
      })
      handleClose()
      if (onSuccess) {
        onSuccess(updatedApproval)
      }
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.status === 409) {
          queryClient.invalidateQueries({
            queryKey: ['organizations', organizationId, 'approvals', approvalId],
          })
          queryClient.invalidateQueries({
            queryKey: ['organizations', organizationId, 'approvals'],
          })
          setError({
            message:
              'This approval request has already been decided or superseded by another reviewer. The authoritative state has been refreshed.',
            code: err.code,
            requestId: err.requestId,
            isConflict: true,
          })
          return
        }

        if (err.status === 403) {
          setError({
            message: 'You do not have permission to reject changes. Only organization owners and admins can reject.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        if (err.status === 404) {
          setError({
            message: 'The requested approval record or organization was not found.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        setError({
          message: err.message || 'Failed to reject change proposal.',
          code: err.code,
          requestId: err.requestId,
        })
      } else {
        setError({
          message: 'An unexpected network error occurred while submitting rejection.',
        })
      }
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setFieldError('')

    const trimmedReason = reason.trim()
    if (!trimmedReason) {
      setFieldError('Rejection reason is required.')
      return
    }

    if (trimmedReason.length > 1000) {
      setFieldError('Rejection reason must not exceed 1000 characters.')
      return
    }

    const payload: RejectApprovalRequest = {
      reason: trimmedReason,
    }

    rejectMutation.mutate(payload)
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={() => {
        if (!rejectMutation.isPending) {
          handleClose()
        }
      }}
      title="Reject Proposed Change"
      description="Decline the code proposal and prevent remote GitHub branches or pull requests from being created."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <ErrorMessage
            title={error.isConflict ? 'Approval Conflict' : 'Rejection Error'}
            message={error.message}
            code={error.code}
            requestId={error.requestId}
          />
        )}

        {/* Warning Callout */}
        <div className="p-3.5 rounded-lg border border-red-500/30 bg-red-950/20 text-xs text-red-200 space-y-1.5">
          <div className="flex items-center gap-2 font-semibold text-red-300">
            <AlertTriangle className="h-4 w-4 shrink-0 text-red-400" />
            <span>Terminal Decision</span>
          </div>
          <p className="text-[11px] text-red-200/80 leading-relaxed">
            Rejecting this proposal will record the decision reason in the task audit event log and transition the approval to a terminal <code className="font-mono text-red-100">REJECTED</code> state.
          </p>
        </div>

        {/* Mandatory Rejection Reason */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="reject-reason" required>
              Rejection Reason
            </Label>
            <span className="text-[10px] text-slate-400 font-mono">
              {reason.length}/1000
            </span>
          </div>
          <textarea
            id="reject-reason"
            rows={4}
            placeholder="Explain why this proposal is being declined (e.g. failing acceptance criteria, security concerns, architectural deviation)..."
            value={reason}
            onChange={(e) => {
              setReason(e.target.value)
              if (fieldError) setFieldError('')
            }}
            maxLength={1000}
            disabled={rejectMutation.isPending}
            className="flex w-full rounded-md border border-slate-800 bg-[#0e131d] px-3.5 py-2 text-xs text-slate-100 shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-red-500/80 focus:border-red-500 disabled:cursor-not-allowed disabled:opacity-50"
            autoFocus
          />
          {fieldError && (
            <p className="text-xs text-red-400 font-medium" role="alert">
              {fieldError}
            </p>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleClose}
            disabled={rejectMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="danger"
            size="sm"
            isLoading={rejectMutation.isPending}
            disabled={rejectMutation.isPending}
          >
            <XOctagon className="h-3.5 w-3.5 mr-1.5" />
            Reject Proposal
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
