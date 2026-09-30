import React, { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FolderPlus } from 'lucide-react'
import { Dialog } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { GithubIcon } from '@/components/common/GithubIcon'
import { repositoriesApi } from '@/api/repositories'
import { ApiClientError } from '@/api/client'
import type { CreateRepositoryRequest, RepositoryResponse } from '@/types/api'

interface CreateRepositoryDialogProps {
  isOpen: boolean
  onClose: () => void
  organizationId: string
  onCreated?: (repo: RepositoryResponse) => void
}

export const CreateRepositoryDialog: React.FC<CreateRepositoryDialogProps> = ({
  isOpen,
  onClose,
  organizationId,
  onCreated,
}) => {
  const queryClient = useQueryClient()
  const [fullName, setFullName] = useState('')
  const [externalId, setExternalId] = useState('')
  const [defaultBranch, setDefaultBranch] = useState('main')
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState<{ message: string; code?: string; requestId?: string } | null>(null)

  const resetForm = () => {
    setFullName('')
    setExternalId('')
    setDefaultBranch('main')
    setFormErrors({})
    setApiError(null)
  }

  const handleClose = () => {
    resetForm()
    onClose()
  }

  const createMutation = useMutation({
    mutationFn: (data: CreateRepositoryRequest) => repositoriesApi.create(organizationId, data),
    onSuccess: (newRepo) => {
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'repositories'],
      })
      handleClose()
      onCreated?.(newRepo)
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.status === 409 || err.code === 'REPOSITORY_ALREADY_EXISTS') {
          setApiError({
            message: 'This repository already exists in this organization.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.status === 403 || err.code === 'FORBIDDEN') {
          setApiError({
            message: 'Only organization Owners and Admins can register repositories.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.violations && err.violations.length > 0) {
          const errors: Record<string, string> = {}
          err.violations.forEach((v) => {
            errors[v.field] = v.message
          })
          setFormErrors(errors)
          setApiError({
            message: 'Please resolve the highlighted validation errors.',
            code: err.code,
            requestId: err.requestId,
          })
        } else {
          setApiError({
            message: err.message || 'Failed to create repository.',
            code: err.code,
            requestId: err.requestId,
          })
        }
      } else {
        setApiError({ message: 'An unexpected error occurred while registering the repository.' })
      }
    },
  })

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    const trimmedFullName = fullName.trim()
    const trimmedExternalId = externalId.trim()
    const trimmedBranch = defaultBranch.trim()

    if (!trimmedFullName) {
      errors.fullName = 'Full repository name is required (e.g. owner/repo)'
    } else if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(trimmedFullName)) {
      errors.fullName = 'Format must be owner/repository (e.g. devflow-inc/backend-api)'
    } else if (trimmedFullName.length > 255) {
      errors.fullName = 'Repository name cannot exceed 255 characters'
    }

    if (!trimmedExternalId) {
      errors.externalId = 'External provider ID is required (e.g. GitHub numeric repository ID)'
    } else if (trimmedExternalId.length > 255) {
      errors.externalId = 'External ID cannot exceed 255 characters'
    }

    if (!trimmedBranch) {
      errors.defaultBranch = 'Default branch is required (e.g. main)'
    } else if (trimmedBranch.length > 255) {
      errors.defaultBranch = 'Branch name cannot exceed 255 characters'
    }

    setFormErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setApiError(null)

    if (!validate()) {
      return
    }

    createMutation.mutate({
      provider: 'GITHUB',
      fullName: fullName.trim(),
      externalId: externalId.trim(),
      defaultBranch: defaultBranch.trim(),
    })
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={handleClose}
      title="Register Repository"
      description="Manually register an existing GitHub repository in this organization."
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-1">
        {apiError && (
          <ErrorMessage
            message={apiError.message}
            code={apiError.code}
            requestId={apiError.requestId}
          />
        )}

        {/* Provider Indicator */}
        <div className="space-y-1.5">
          <label className="block text-xs font-medium text-slate-300">
            Repository Provider
          </label>
          <div className="flex items-center gap-2 px-3 py-2 rounded-md bg-slate-900 border border-slate-800 text-xs text-slate-200">
            <GithubIcon className="h-4 w-4 text-white" />
            <span className="font-semibold">GitHub</span>
            <span className="text-[11px] text-slate-400 ml-auto font-mono">Provider: GITHUB</span>
          </div>
        </div>

        {/* Full Name */}
        <div className="space-y-1.5">
          <label htmlFor="repo-fullname" className="block text-xs font-medium text-slate-300">
            Repository Full Name <span className="text-red-400">*</span>
          </label>
          <Input
            id="repo-fullname"
            placeholder="e.g. devflow-inc/api-service"
            value={fullName}
            onChange={(e) => {
              setFullName(e.target.value)
              if (formErrors.fullName) {
                setFormErrors((prev) => ({ ...prev, fullName: '' }))
              }
            }}
            disabled={createMutation.isPending}
            error={formErrors.fullName}
            autoFocus
          />
          <p className="text-[11px] text-slate-500">
            Format: owner/name on GitHub.
          </p>
        </div>

        {/* External ID */}
        <div className="space-y-1.5">
          <label htmlFor="repo-externalid" className="block text-xs font-medium text-slate-300">
            External Provider ID <span className="text-red-400">*</span>
          </label>
          <Input
            id="repo-externalid"
            placeholder="e.g. 102938475"
            value={externalId}
            onChange={(e) => {
              setExternalId(e.target.value)
              if (formErrors.externalId) {
                setFormErrors((prev) => ({ ...prev, externalId: '' }))
              }
            }}
            disabled={createMutation.isPending}
            error={formErrors.externalId}
          />
          <p className="text-[11px] text-slate-500">
            The external repository identifier from GitHub API or webhook payload.
          </p>
        </div>

        {/* Default Branch */}
        <div className="space-y-1.5">
          <label htmlFor="repo-branch" className="block text-xs font-medium text-slate-300">
            Default Branch <span className="text-red-400">*</span>
          </label>
          <Input
            id="repo-branch"
            placeholder="main"
            value={defaultBranch}
            onChange={(e) => {
              setDefaultBranch(e.target.value)
              if (formErrors.defaultBranch) {
                setFormErrors((prev) => ({ ...prev, defaultBranch: '' }))
              }
            }}
            disabled={createMutation.isPending}
            error={formErrors.defaultBranch}
          />
        </div>

        <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleClose}
            disabled={createMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            isLoading={createMutation.isPending}
          >
            <FolderPlus className="h-3.5 w-3.5 mr-1.5" />
            Register Repository
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
