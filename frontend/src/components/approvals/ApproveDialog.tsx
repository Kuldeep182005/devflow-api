import React, { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ShieldCheck } from 'lucide-react'
import { Dialog } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { approvalsApi } from '@/api/approvals'
import { ApiClientError } from '@/api/client'
import type { ApprovalResponse, ApproveApprovalRequest } from '@/types/api'

interface ApproveDialogProps {
  isOpen: boolean
  onClose: () => void
  organizationId: string
  approvalId: string
  taskId: string
  onSuccess?: (updatedApproval: ApprovalResponse) => void
}

export const ApproveDialog: React.FC<ApproveDialogProps> = ({
  isOpen,
  onClose,
  organizationId,
  approvalId,
  taskId,
  onSuccess,
}) => {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [error, setError] = useState<{
    message: string
    code?: string
    requestId?: string
    isConflict?: boolean
  } | null>(null)

  const handleClose = () => {
    setComment('')
    setError(null)
    onClose()
  }

  const approveMutation = useMutation({
    mutationFn: (payload: ApproveApprovalRequest) =>
      approvalsApi.approve(organizationId, approvalId, payload),
    onSuccess: (updatedApproval) => {
      // Invalidate both approval queries and task queries
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
          // Stale approval or already decided by another reviewer
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
            message: 'You do not have permission to approve changes. Only organization owners and admins can approve.',
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
          message: err.message || 'Failed to approve change.',
          code: err.code,
          requestId: err.requestId,
        })
      } else {
        setError({
          message: 'An unexpected network error occurred while submitting approval.',
        })
      }
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (comment.length > 1000) {
      setError({
        message: 'Comment must not exceed 1000 characters.',
      })
      return
    }

    const payload: ApproveApprovalRequest = {
      comment: comment.trim() || undefined,
    }

    approveMutation.mutate(payload)
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={() => {
        if (!approveMutation.isPending) {
          handleClose()
        }
      }}
      title="Approve Proposed Change"
      description="Authorize the verified code changes for downstream execution on GitHub."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <ErrorMessage
            title={error.isConflict ? 'Approval Conflict' : 'Approval Error'}
            message={error.message}
            code={error.code}
            requestId={error.requestId}
          />
        )}

        {/* Security Consequential Notice */}
        <div className="p-3.5 rounded-lg border border-emerald-500/30 bg-emerald-950/20 text-xs text-emerald-200 space-y-1.5">
          <div className="flex items-center gap-2 font-semibold text-emerald-300">
            <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-400" />
            <span>Consequential Mutation Authorization</span>
          </div>
          <p className="text-[11px] text-emerald-200/80 leading-relaxed">
            By approving this proposal, you authorize the platform to transition the task to an approved state and permit remote branch push, commit creation, and pull request generation on GitHub.
          </p>
        </div>

        {/* Optional Review Comment */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="approve-comment">
              Review Comment <span className="text-slate-400 font-normal">(optional)</span>
            </Label>
            <span className="text-[10px] text-slate-400 font-mono">
              {comment.length}/1000
            </span>
          </div>
          <textarea
            id="approve-comment"
            rows={3}
            placeholder="Add an optional review note or sign-off message for the audit trail..."
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={1000}
            disabled={approveMutation.isPending}
            className="flex w-full rounded-md border border-slate-800 bg-[#0e131d] px-3.5 py-2 text-xs text-slate-100 shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-500/80 focus:border-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
          />
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleClose}
            disabled={approveMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            isLoading={approveMutation.isPending}
            disabled={approveMutation.isPending}
            className="bg-emerald-600 hover:bg-emerald-500 text-white"
          >
            <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" />
            Approve & Authorize
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
