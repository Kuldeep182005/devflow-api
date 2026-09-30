import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Play, AlertCircle } from 'lucide-react'
import { Dialog } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import { ApiClientError } from '@/api/client'
import type { TaskResponse, CreateTaskRequest } from '@/types/api'

interface CreateTaskDialogProps {
  isOpen: boolean
  onClose: () => void
  organizationId: string
  defaultRepositoryId?: string
  onSuccess?: (task: TaskResponse) => void
}

export const CreateTaskDialog: React.FC<CreateTaskDialogProps> = ({
  isOpen,
  onClose,
  organizationId,
  defaultRepositoryId,
  onSuccess,
}) => {
  const queryClient = useQueryClient()

  // Form State
  const [repositoryId, setRepositoryId] = useState(defaultRepositoryId || '')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  // Validation & Error State
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<{
    message: string
    code?: string
    requestId?: string
  } | null>(null)

  // Load repositories for selection
  const {
    data: repositoriesData,
    isLoading: isLoadingRepos,
    isError: isReposError,
  } = useQuery({
    queryKey: ['organizations', organizationId, 'repositories', { size: 100 }],
    queryFn: () => repositoriesApi.list(organizationId, { size: 100 }),
    enabled: isOpen && !!organizationId,
  })

  const repositories = repositoriesData?.content || []
  const effectiveRepoId =
    repositoryId ||
    defaultRepositoryId ||
    (repositories.length === 1 ? repositories[0].id : '')

  const handleClose = () => {
    setTitle('')
    setDescription('')
    setFieldErrors({})
    setSubmitError(null)
    setRepositoryId(defaultRepositoryId || '')
    onClose()
  }

  // Validation logic matching backend constraints
  const validate = (): boolean => {
    const errors: Record<string, string> = {}

    if (!effectiveRepoId.trim()) {
      errors.repositoryId = 'Please select a target repository'
    }

    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      errors.title = 'Task title is required'
    } else if (trimmedTitle.length > 255) {
      errors.title = 'Task title must be 255 characters or fewer'
    }

    if (description && description.length > 4000) {
      errors.description = 'Description must be 4000 characters or fewer'
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (payload: CreateTaskRequest) => tasksApi.create(organizationId, payload),
    onSuccess: (newTask: TaskResponse) => {
      // Invalidate relevant queries
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks'],
      })
      handleClose()
      if (onSuccess) {
        onSuccess(newTask)
      }
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.status === 400 && err.violations && err.violations.length > 0) {
          const mapped: Record<string, string> = {}
          err.violations.forEach((v) => {
            mapped[v.field] = v.message
          })
          setFieldErrors(mapped)
          setSubmitError({
            message: 'Please review the highlighted errors below.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        if (err.status === 403) {
          setSubmitError({
            message: 'You do not have permission to dispatch tasks in this organization.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        if (err.status === 404) {
          setSubmitError({
            message: 'Target repository or organization not found.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        if (err.status === 409) {
          setSubmitError({
            message: 'A conflicting task or repository state prevented task dispatch.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        if (err.status === 429) {
          setSubmitError({
            message: 'Task creation rate limit exceeded. Please wait a moment before trying again.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }

        setSubmitError({
          message: err.message || 'Failed to dispatch task.',
          code: err.code,
          requestId: err.requestId,
        })
      } else {
        setSubmitError({
          message: 'An unexpected network error occurred while creating the task.',
        })
      }
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitError(null)

    if (!validate()) {
      return
    }

    const payload: CreateTaskRequest = {
      repositoryId: effectiveRepoId,
      title: title.trim(),
      ...(description.trim() ? { description: description.trim() } : {}),
    }

    createMutation.mutate(payload)
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={() => {
        if (!createMutation.isPending) {
          handleClose()
        }
      }}
      title="Dispatch AI Task"
      description="Create a new engineering task for autonomous agent analysis, patch formulation, and isolated sandbox verification."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {submitError && (
          <ErrorMessage
            title="Dispatch Error"
            message={submitError.message}
            code={submitError.code}
            requestId={submitError.requestId}
          />
        )}

        {/* Repository Selection */}
        <div className="space-y-1.5">
          <Label htmlFor="task-repository" required>
            Target Repository
          </Label>
          {isLoadingRepos ? (
            <div className="h-10 rounded-md bg-slate-900 animate-pulse border border-slate-800" />
          ) : isReposError ? (
            <p className="text-xs text-red-400 flex items-center gap-1">
              <AlertCircle className="h-3.5 w-3.5" />
              Failed to load repositories.
            </p>
          ) : repositories.length === 0 ? (
            <div className="p-3 rounded-lg border border-amber-500/30 bg-amber-950/20 text-amber-300 text-xs">
              No repositories found. Please connect a repository to this organization first before dispatching tasks.
            </div>
          ) : (
            <Select
              id="task-repository"
              value={effectiveRepoId}
              onChange={(e) => {
                setRepositoryId(e.target.value)
                if (fieldErrors.repositoryId) {
                  setFieldErrors((prev) => ({ ...prev, repositoryId: '' }))
                }
              }}
              error={fieldErrors.repositoryId}
              disabled={createMutation.isPending}
            >
              <option value="" disabled>
                Select a connected repository...
              </option>
              {repositories.map((repo) => (
                <option key={repo.id} value={repo.id}>
                  {repo.fullName} ({repo.defaultBranch})
                </option>
              ))}
            </Select>
          )}
        </div>

        {/* Task Title */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="task-title" required>
              Task Title
            </Label>
            <span className="text-[10px] text-slate-400 font-mono">
              {title.length}/255
            </span>
          </div>
          <Input
            id="task-title"
            placeholder="e.g. Fix authentication token expiration handling in auth interceptor"
            value={title}
            onChange={(e) => {
              setTitle(e.target.value)
              if (fieldErrors.title) {
                setFieldErrors((prev) => ({ ...prev, title: '' }))
              }
            }}
            maxLength={255}
            error={fieldErrors.title}
            disabled={createMutation.isPending}
            autoFocus
          />
        </div>

        {/* Description / Instructions */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="task-description">
              Instructions & Context <span className="text-slate-400 font-normal">(optional)</span>
            </Label>
            <span className="text-[10px] text-slate-400 font-mono">
              {description.length}/4000
            </span>
          </div>
          <textarea
            id="task-description"
            rows={4}
            placeholder="Provide specific requirements, failing test descriptions, or context for the AI agent..."
            value={description}
            onChange={(e) => {
              setDescription(e.target.value)
              if (fieldErrors.description) {
                setFieldErrors((prev) => ({ ...prev, description: '' }))
              }
            }}
            maxLength={4000}
            disabled={createMutation.isPending}
            className="flex w-full rounded-md border border-slate-800 bg-[#0e131d] px-3.5 py-2 text-xs text-slate-100 shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500/80 focus:border-blue-500 disabled:cursor-not-allowed disabled:opacity-50"
          />
          {fieldErrors.description && (
            <p className="text-xs text-red-400 font-medium" role="alert">
              {fieldErrors.description}
            </p>
          )}
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onClose}
            disabled={createMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            isLoading={createMutation.isPending}
            disabled={createMutation.isPending || repositories.length === 0}
          >
            <Play className="h-3.5 w-3.5 mr-1.5" />
            Dispatch Task
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
