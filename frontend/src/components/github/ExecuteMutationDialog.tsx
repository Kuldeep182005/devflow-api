import React, { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { GitPullRequest, ShieldAlert } from 'lucide-react'
import { Dialog } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { approvalsApi } from '@/api/approvals'
import { ApiClientError } from '@/api/client'
import type { GithubMutationResponse } from '@/types/api'

interface ExecuteMutationDialogProps {
  isOpen: boolean
  onClose: () => void
  organizationId: string
  approvalId: string
  taskId: string
  repositoryFullName?: string
  branchName?: string
  onSuccess?: (mutation: GithubMutationResponse) => void
}

export const ExecuteMutationDialog: React.FC<ExecuteMutationDialogProps> = ({
  isOpen,
  onClose,
  organizationId,
  approvalId,
  taskId,
  repositoryFullName,
  branchName,
  onSuccess,
}) => {
  const queryClient = useQueryClient()
  const [error, setError] = useState<{
    message: string
    code?: string
    requestId?: string
    isConflict?: boolean
  } | null>(null)

  const handleClose = () => {
    setError(null)
    onClose()
  }

  const executeMutation = useMutation({
    mutationFn: () => approvalsApi.execute(organizationId, approvalId),
    onSuccess: (mutationResponse) => {
      // Set mutation query cache and invalidate related queries
      queryClient.setQueryData(
        ['organizations', organizationId, 'approvals', approvalId, 'mutation'],
        mutationResponse
      )
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'approvals', approvalId],
      })
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks', taskId],
      })
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks', taskId, 'events'],
      })
      handleClose()
      if (onSuccess) {
        onSuccess(mutationResponse)
      }
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.status === 409) {
          // Concurrency or precondition conflict: refresh authoritative state
          queryClient.invalidateQueries({
            queryKey: ['organizations', organizationId, 'approvals', approvalId],
          })
          queryClient.invalidateQueries({
            queryKey: ['organizations', organizationId, 'approvals', approvalId, 'mutation'],
          })
          setError({
            message:
              err.message ||
              'A conflict occurred during mutation execution. The authoritative state has been refreshed.',
            code: err.code,
            requestId: err.requestId,
            isConflict: true,
          })
          return
        }

        if (err.status === 403) {
          setError({
            message:
              'You do not have permission to execute GitHub mutations. Only organization owners and admins can trigger execution.',
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
          message: err.message || 'Failed to dispatch GitHub mutation execution.',
          code: err.code,
          requestId: err.requestId,
        })
      } else {
        setError({
          message: 'An unexpected network error occurred while dispatching execution.',
        })
      }
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    executeMutation.mutate()
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={() => {
        if (!executeMutation.isPending) {
          handleClose()
        }
      }}
      title="Execute GitHub Change"
      description="Authorize downstream remote repository modification based on human approval."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <ErrorMessage
            title={error.isConflict ? 'Execution Conflict' : 'Execution Error'}
            message={error.message}
            code={error.code}
            requestId={error.requestId}
          />
        )}

        {/* Security Consequential Warning */}
        <div className="p-3.5 rounded-lg border border-sky-500/30 bg-sky-950/20 text-xs text-sky-200 space-y-2">
          <div className="flex items-center gap-2 font-semibold text-sky-300">
            <ShieldAlert className="h-4 w-4 shrink-0 text-sky-400" />
            <span>Remote GitHub Mutation Notice</span>
          </div>
          <p className="text-[11px] text-sky-200/90 leading-relaxed">
            Executing this approved change will cause consequential GitHub-side changes. The platform will:
          </p>
          <ul className="list-disc pl-5 space-y-1 text-[11px] text-sky-200/80">
            <li>Create or update a remote branch on target repository</li>
            <li>Commit the approved patch using the repository's GitHub App installation</li>
            <li>Open a Pull Request with complete audit metadata</li>
          </ul>
        </div>

        {/* Target Details */}
        <div className="rounded-lg border border-slate-800 bg-[#0e131d] p-3 text-xs space-y-2 font-mono">
          {repositoryFullName && (
            <div className="flex items-center justify-between text-slate-300">
              <span className="text-slate-400 text-[11px]">Repository:</span>
              <span className="text-slate-100 font-semibold">{repositoryFullName}</span>
            </div>
          )}
          {branchName && (
            <div className="flex items-center justify-between text-slate-300">
              <span className="text-slate-400 text-[11px]">Task Branch:</span>
              <span className="text-slate-100">{branchName}</span>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleClose}
            disabled={executeMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            isLoading={executeMutation.isPending}
            disabled={executeMutation.isPending}
            className="bg-blue-600 hover:bg-blue-500 text-white"
          >
            <GitPullRequest className="h-3.5 w-3.5 mr-1.5" />
            Confirm & Execute on GitHub
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
