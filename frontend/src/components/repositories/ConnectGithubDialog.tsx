import React, { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link2, Info } from 'lucide-react'
import { Dialog } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { GithubIcon } from '@/components/common/GithubIcon'
import { repositoriesApi } from '@/api/repositories'
import { ApiClientError } from '@/api/client'
import type { ConnectRepositoryRequest, RepositoryResponse } from '@/types/api'

interface ConnectGithubDialogProps {
  isOpen: boolean
  onClose: () => void
  organizationId: string
  organizationSlug?: string
  onConnected?: (repo: RepositoryResponse) => void
}

export const ConnectGithubDialog: React.FC<ConnectGithubDialogProps> = ({
  isOpen,
  onClose,
  organizationId,
  organizationSlug = '',
  onConnected,
}) => {
  const queryClient = useQueryClient()
  const [owner, setOwner] = useState(organizationSlug)
  const [name, setName] = useState('')
  const [installationId, setInstallationId] = useState('')
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState<{ message: string; code?: string; requestId?: string } | null>(null)

  const resetForm = () => {
    setOwner(organizationSlug)
    setName('')
    setInstallationId('')
    setFormErrors({})
    setApiError(null)
  }

  const handleClose = () => {
    resetForm()
    onClose()
  }

  const connectMutation = useMutation({
    mutationFn: (data: ConnectRepositoryRequest) =>
      repositoriesApi.connect(organizationId, data),
    onSuccess: (newRepo) => {
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'repositories'],
      })
      handleClose()
      onConnected?.(newRepo)
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.code === 'GITHUB_INSTALLATION_NOT_FOUND') {
          setApiError({
            message:
              'The GitHub App is not installed for this organization account. Please install the DevFlow GitHub App on your GitHub account before connecting.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.code === 'REPOSITORY_ORGANIZATION_MISMATCH') {
          setApiError({
            message:
              `The repository owner must match your organization slug ("${organizationSlug || 'organization'}"). Cross-organization repository linking is rejected by backend security.`,
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.code === 'GITHUB_INSTALLATION_FORBIDDEN') {
          setApiError({
            message: 'The GitHub App installation is not authorized for this organization.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.status === 404 || err.code === 'REPOSITORY_NOT_FOUND') {
          setApiError({
            message: 'Repository not found on GitHub or the GitHub App lacks permissions to access it.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.code === 'REPOSITORY_ALREADY_CONNECTED') {
          setApiError({
            message: 'This repository is already connected to another organization.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.code === 'REPOSITORY_ALREADY_EXISTS') {
          setApiError({
            message: 'This repository is already connected in this organization.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.status === 503 || err.code === 'GITHUB_NOT_CONFIGURED') {
          setApiError({
            message: 'GitHub App integration is not configured on this server.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.status === 403 || err.code === 'FORBIDDEN') {
          setApiError({
            message: 'Only organization Owners and Admins can connect repositories.',
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
            message: err.message || 'Failed to connect repository via GitHub integration.',
            code: err.code,
            requestId: err.requestId,
          })
        }
      } else {
        setApiError({ message: 'An unexpected error occurred while connecting the repository.' })
      }
    },
  })

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    const trimmedOwner = owner.trim()
    const trimmedName = name.trim()
    const githubNameRegex = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/

    if (!trimmedOwner) {
      errors.owner = 'GitHub owner or organization is required'
    } else if (!githubNameRegex.test(trimmedOwner)) {
      errors.owner = 'Owner must start with alphanumeric characters and be up to 100 characters'
    }

    if (!trimmedName) {
      errors.name = 'Repository name is required'
    } else if (!githubNameRegex.test(trimmedName)) {
      errors.name = 'Repository name must start with alphanumeric characters and be up to 100 characters'
    }

    if (installationId.trim()) {
      const parsedId = Number(installationId.trim())
      if (!Number.isInteger(parsedId) || parsedId <= 0) {
        errors.installationId = 'Installation ID must be a positive integer'
      }
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

    const payload: ConnectRepositoryRequest = {
      provider: 'GITHUB',
      owner: owner.trim(),
      name: name.trim(),
    }

    if (installationId.trim()) {
      payload.installationId = Number(installationId.trim())
    }

    connectMutation.mutate(payload)
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={handleClose}
      title="Connect GitHub Repository"
      description="Connect a repository via the DevFlow GitHub App integration."
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-1">
        {apiError && (
          <ErrorMessage
            message={apiError.message}
            code={apiError.code}
            requestId={apiError.requestId}
          />
        )}

        {/* Informative Guidance Banner */}
        <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/20 text-xs text-blue-300 flex items-start gap-2.5">
          <Info className="h-4 w-4 text-blue-400 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <p className="font-semibold text-blue-200">GitHub App Authorization</p>
            <p className="text-blue-300/90 leading-relaxed">
              The DevFlow GitHub App must be installed on your GitHub organization. By security rule, the GitHub owner must match this organization slug: <span className="font-mono font-bold text-white">{organizationSlug || 'organization'}</span>.
            </p>
          </div>
        </div>

        {/* Provider Indicator */}
        <div className="space-y-1.5">
          <label className="block text-xs font-medium text-slate-300">
            Provider
          </label>
          <div className="flex items-center gap-2 px-3 py-2 rounded-md bg-slate-900 border border-slate-800 text-xs text-slate-200">
            <GithubIcon className="h-4 w-4 text-white" />
            <span className="font-semibold">GitHub Integration</span>
            <span className="text-[11px] text-emerald-400 ml-auto font-mono bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
              Verified Integration
            </span>
          </div>
        </div>

        {/* GitHub Owner */}
        <div className="space-y-1.5">
          <label htmlFor="connect-owner" className="block text-xs font-medium text-slate-300">
            GitHub Owner / Account <span className="text-red-400">*</span>
          </label>
          <Input
            id="connect-owner"
            placeholder={organizationSlug || 'e.g. devflow-inc'}
            value={owner}
            onChange={(e) => {
              setOwner(e.target.value)
              if (formErrors.owner) {
                setFormErrors((prev) => ({ ...prev, owner: '' }))
              }
            }}
            disabled={connectMutation.isPending}
            error={formErrors.owner}
            autoFocus
          />
          <p className="text-[11px] text-slate-500">
            Organization or user name on GitHub matching your DevFlow workspace.
          </p>
        </div>

        {/* Repository Name */}
        <div className="space-y-1.5">
          <label htmlFor="connect-name" className="block text-xs font-medium text-slate-300">
            Repository Name <span className="text-red-400">*</span>
          </label>
          <Input
            id="connect-name"
            placeholder="e.g. core-api"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              if (formErrors.name) {
                setFormErrors((prev) => ({ ...prev, name: '' }))
              }
            }}
            disabled={connectMutation.isPending}
            error={formErrors.name}
          />
        </div>

        {/* Optional Installation ID */}
        <div className="space-y-1.5">
          <label htmlFor="connect-installation" className="block text-xs font-medium text-slate-300">
            Installation ID <span className="text-slate-500 font-normal">(Optional)</span>
          </label>
          <Input
            id="connect-installation"
            placeholder="e.g. 54321098"
            value={installationId}
            onChange={(e) => {
              setInstallationId(e.target.value)
              if (formErrors.installationId) {
                setFormErrors((prev) => ({ ...prev, installationId: '' }))
              }
            }}
            disabled={connectMutation.isPending}
            error={formErrors.installationId}
          />
          <p className="text-[11px] text-slate-500">
            Leave blank unless your account has multiple GitHub App installations.
          </p>
        </div>

        <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleClose}
            disabled={connectMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            isLoading={connectMutation.isPending}
          >
            <Link2 className="h-3.5 w-3.5 mr-1.5" />
            Connect Repository
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
