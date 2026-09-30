import React, { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  GitBranch,
  ExternalLink,
  Copy,
  Check,
  Calendar,
  Fingerprint,
  Building2,
  ShieldCheck,
  CheckSquare,
  ArrowLeft,
  CircleDot,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { GithubIcon } from '@/components/common/GithubIcon'
import { repositoriesApi } from '@/api/repositories'
import { organizationsApi } from '@/api/organizations'
import { getSafeGithubUrl } from '@/lib/urls'

export const RepositoryDetailPage: React.FC = () => {
  const { organizationId, repositoryId } = useParams<{
    organizationId: string
    repositoryId: string
  }>()

  const [copiedField, setCopiedField] = useState<string | null>(null)

  const copyToClipboard = (text: string, fieldName: string) => {
    navigator.clipboard.writeText(text)
    setCopiedField(fieldName)
    setTimeout(() => setCopiedField(null), 2000)
  }

  // Fetch repository details from backend
  const {
    data: repository,
    isLoading: isRepoLoading,
    isError: isRepoError,
    error: repoError,
    refetch: refetchRepo,
  } = useQuery({
    queryKey: ['organizations', organizationId, 'repositories', repositoryId],
    queryFn: () => repositoriesApi.get(organizationId!, repositoryId!),
    enabled: !!organizationId && !!repositoryId,
  })

  // Fetch parent organization metadata
  const { data: organization } = useQuery({
    queryKey: ['organizations', organizationId],
    queryFn: () => organizationsApi.get(organizationId!),
    enabled: !!organizationId,
  })

  if (isRepoLoading) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full">
        <LoadingSpinner size="lg" label="Loading repository details..." />
      </div>
    )
  }

  if (isRepoError || !repository) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full space-y-4">
        <Link
          to={`/organizations/${organizationId}/repositories`}
          className="text-xs text-blue-400 hover:text-blue-300 inline-flex items-center gap-1"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Repositories
        </Link>
        <ErrorMessage
          title="Repository not found"
          message={
            repoError instanceof Error
              ? repoError.message
              : 'The requested repository could not be found or you do not have permission to view it.'
          }
          onRetry={() => refetchRepo()}
        />
      </div>
    )
  }

  const safeExternalUrl = getSafeGithubUrl(repository.fullName)

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
      {/* Breadcrumbs */}
      <div className="flex items-center gap-2 text-xs text-slate-400">
        <Link to="/organizations" className="hover:text-slate-200">
          Organizations
        </Link>
        <span>/</span>
        <Link
          to={`/organizations/${organizationId}`}
          className="hover:text-slate-200"
        >
          {organization?.name || 'Organization'}
        </Link>
        <span>/</span>
        <Link
          to={`/organizations/${organizationId}/repositories`}
          className="hover:text-slate-200"
        >
          Repositories
        </Link>
        <span>/</span>
        <span className="text-slate-200 font-medium truncate max-w-xs">
          {repository.fullName}
        </span>
      </div>

      {/* Header Bar */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-slate-800/90 border border-slate-700/60 flex items-center justify-center text-white shrink-0">
              <GithubIcon className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2.5 flex-wrap">
                <h1 className="text-xl font-bold text-white tracking-tight">
                  {repository.fullName}
                </h1>
                <Badge variant="neutral" className="font-mono text-xs">
                  {repository.provider}
                </Badge>
                <Badge variant={repository.active ? 'success' : 'neutral'}>
                  {repository.active ? 'Active' : 'Inactive'}
                </Badge>
                <span className="flex items-center gap-1 text-xs font-mono text-slate-300 bg-slate-800 px-2 py-0.5 rounded border border-slate-700/60">
                  <GitBranch className="h-3.5 w-3.5 text-slate-400" />
                  {repository.defaultBranch}
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-1">
                Connected codebase in{' '}
                <strong className="text-slate-300 font-medium">
                  {organization?.name || 'this organization'}
                </strong>
                .
              </p>
            </div>
          </div>
        </div>

        {/* Header Actions */}
        <div className="flex items-center gap-2.5">
          <Link to={`/organizations/${organizationId}/repositories`}>
            <Button variant="outline" size="sm" className="gap-1.5">
              <ArrowLeft className="h-3.5 w-3.5" />
              All Repositories
            </Button>
          </Link>
          {safeExternalUrl && (
            <a
              href={safeExternalUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex"
            >
              <Button variant="primary" size="sm" className="gap-1.5">
                <GithubIcon className="h-3.5 w-3.5" />
                View on GitHub
                <ExternalLink className="h-3 w-3 ml-0.5 opacity-70" />
              </Button>
            </a>
          )}
        </div>
      </div>

      {/* Overview Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Repository ID */}
        <div className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 space-y-1.5">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span className="flex items-center gap-1.5">
              <Fingerprint className="h-3.5 w-3.5 text-slate-500" />
              Repository ID
            </span>
            <button
              onClick={() => copyToClipboard(repository.id, 'repoId')}
              className="text-slate-500 hover:text-slate-300 transition-colors"
              title="Copy ID"
              aria-label="Copy Repository ID"
            >
              {copiedField === 'repoId' ? (
                <Check className="h-3.5 w-3.5 text-emerald-400" />
              ) : (
                <Copy className="h-3.5 w-3.5" />
              )}
            </button>
          </div>
          <p className="text-xs font-mono font-medium text-slate-200 truncate">
            {repository.id}
          </p>
        </div>

        {/* External ID */}
        <div className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 space-y-1.5">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span className="flex items-center gap-1.5">
              <GithubIcon className="h-3.5 w-3.5 text-slate-500" />
              GitHub External ID
            </span>
            <button
              onClick={() => copyToClipboard(repository.externalId, 'extId')}
              className="text-slate-500 hover:text-slate-300 transition-colors"
              title="Copy External ID"
              aria-label="Copy External ID"
            >
              {copiedField === 'extId' ? (
                <Check className="h-3.5 w-3.5 text-emerald-400" />
              ) : (
                <Copy className="h-3.5 w-3.5" />
              )}
            </button>
          </div>
          <p className="text-xs font-mono font-medium text-slate-200 truncate">
            {repository.externalId}
          </p>
        </div>

        {/* Default Branch */}
        <div className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 space-y-1.5">
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <GitBranch className="h-3.5 w-3.5 text-slate-500" />
            Default Branch
          </div>
          <p className="text-xs font-mono font-medium text-slate-200 truncate">
            {repository.defaultBranch}
          </p>
        </div>

        {/* Created Date */}
        <div className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 space-y-1.5">
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <Calendar className="h-3.5 w-3.5 text-slate-500" />
            Connected On
          </div>
          <p className="text-xs font-medium text-slate-200">
            {new Date(repository.createdAt).toLocaleDateString(undefined, {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })}
          </p>
        </div>
      </div>

      {/* GitHub Integration Status Card */}
      <div className="p-6 rounded-xl border border-slate-800/80 bg-slate-900/30 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <CircleDot className="h-4 w-4 text-emerald-400" />
            <h2 className="text-sm font-semibold text-white">GitHub Integration Status</h2>
          </div>
          <Badge variant={repository.active ? 'success' : 'neutral'} className="text-xs">
            {repository.active ? 'Verified & Active' : 'Inactive'}
          </Badge>
        </div>
        <p className="text-xs text-slate-400 leading-relaxed max-w-3xl">
          This repository is registered with DevFlow under provider <strong className="text-slate-300">GitHub</strong>.
          The backend GitHub App integration handles automated branch management, workspace checkout, and code patch verification.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2 border-t border-slate-800/60 text-xs text-slate-400">
          <div>
            <span className="text-slate-500 block mb-0.5">Target Organization</span>
            <span className="text-slate-200 font-medium">{organization?.name || organizationId}</span>
          </div>
          <div>
            <span className="text-slate-500 block mb-0.5">Repository Coordinates</span>
            <span className="text-slate-200 font-mono">{repository.fullName}</span>
          </div>
          <div>
            <span className="text-slate-500 block mb-0.5">Last Metadata Sync</span>
            <span className="text-slate-200 font-medium">
              {new Date(repository.updatedAt).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
              })}{' '}
              on {new Date(repository.updatedAt).toLocaleDateString()}
            </span>
          </div>
        </div>
      </div>

      {/* Workflow Navigation Shortcuts */}
      <div className="space-y-3">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          Engineering Workflows
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Link
            to={`/organizations/${organizationId}/tasks`}
            className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 hover:bg-slate-900 hover:border-slate-700 transition-colors group flex items-start gap-3"
          >
            <div className="h-8 w-8 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 shrink-0">
              <CheckSquare className="h-4 w-4" />
            </div>
            <div className="space-y-1">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-200 group-hover:text-blue-400 transition-colors">
                Organization Tasks
                <span aria-hidden="true">→</span>
              </div>
              <p className="text-[11px] text-slate-400">
                View queued, running, and completed tasks targeting this organization.
              </p>
            </div>
          </Link>

          <Link
            to={`/organizations/${organizationId}/approvals`}
            className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 hover:bg-slate-900 hover:border-slate-700 transition-colors group flex items-start gap-3"
          >
            <div className="h-8 w-8 rounded-lg bg-purple-500/10 border border-purple-500/20 flex items-center justify-center text-purple-400 shrink-0">
              <ShieldCheck className="h-4 w-4" />
            </div>
            <div className="space-y-1">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-200 group-hover:text-purple-400 transition-colors">
                Change Approvals
                <span aria-hidden="true">→</span>
              </div>
              <p className="text-[11px] text-slate-400">
                Review verified code patches and sandbox results requiring approval.
              </p>
            </div>
          </Link>

          <Link
            to={`/organizations/${organizationId}`}
            className="p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 hover:bg-slate-900 hover:border-slate-700 transition-colors group flex items-start gap-3"
          >
            <div className="h-8 w-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0">
              <Building2 className="h-4 w-4" />
            </div>
            <div className="space-y-1">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-200 group-hover:text-emerald-400 transition-colors">
                Organization Overview
                <span aria-hidden="true">→</span>
              </div>
              <p className="text-[11px] text-slate-400">
                Manage team members, roles, and workspace configuration.
              </p>
            </div>
          </Link>
        </div>
      </div>
    </div>
  )
}
