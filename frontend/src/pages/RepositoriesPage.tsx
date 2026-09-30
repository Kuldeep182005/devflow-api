import React, { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  FolderGit2,
  GitBranch,
  ExternalLink,
  Plus,
  Link2,
  Search,
  Building2,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { EmptyState } from '@/components/common/EmptyState'
import { GithubIcon } from '@/components/common/GithubIcon'
import { CreateRepositoryDialog } from '@/components/repositories/CreateRepositoryDialog'
import { ConnectGithubDialog } from '@/components/repositories/ConnectGithubDialog'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { useAuth } from '@/hooks/useAuth'
import { repositoriesApi } from '@/api/repositories'
import { organizationsApi } from '@/api/organizations'
import { getSafeGithubUrl } from '@/lib/urls'
import type { RepositoryResponse } from '@/types/api'

export const RepositoriesPage: React.FC = () => {
  const params = useParams<{ organizationId?: string }>()
  const { activeOrganization, organizations } = useActiveOrganization()
  const { session } = useAuth()

  // Resolve target organization from route params or active context
  const resolvedOrgId = params.organizationId || activeOrganization?.id || null
  const currentOrg = organizations.find((o) => o.id === resolvedOrgId) || activeOrganization

  const [page, setPage] = useState(0)
  const pageSize = 20
  const [searchQuery, setSearchQuery] = useState('')
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isConnectOpen, setIsConnectOpen] = useState(false)

  // Query organization repositories with pagination
  const {
    data: repositoriesPage,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['organizations', resolvedOrgId, 'repositories', { page, size: pageSize }],
    queryFn: () =>
      repositoriesApi.list(resolvedOrgId!, {
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      }),
    enabled: !!resolvedOrgId,
  })

  // Query organization members to determine current user's role (for UX hints)
  const { data: membersPage } = useQuery({
    queryKey: ['organizations', resolvedOrgId, 'members'],
    queryFn: () => organizationsApi.listMembers(resolvedOrgId!),
    enabled: !!resolvedOrgId,
  })

  const currentMember = membersPage?.content?.find((m) => m.userId === session?.userId)
  const currentUserRole = currentMember?.role
  // UX check: only OWNER and ADMIN can mutate repositories in backend
  const canManageRepos = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN'

  // If no organization is selected anywhere
  if (!resolvedOrgId) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full space-y-6">
        <div className="border-b border-slate-800/80 pb-4">
          <h1 className="text-xl font-bold text-white tracking-tight">Repositories</h1>
          <p className="text-xs text-slate-400 mt-1">
            Connected repositories and codebases managed across your DevFlow workspaces.
          </p>
        </div>

        <EmptyState
          title="No Organization Selected"
          description="Select an organization from the workspace switcher or create one to view and connect repositories."
          icon={<Building2 className="h-8 w-8 text-slate-500" />}
        >
          <div className="flex items-center gap-3">
            <Link to="/organizations">
              <Button variant="primary" size="sm">
                <Building2 className="h-3.5 w-3.5 mr-1.5" />
                View Organizations
              </Button>
            </Link>
          </div>
        </EmptyState>
      </div>
    )
  }

  const allRepositories = repositoriesPage?.content || []
  const filteredRepositories = allRepositories.filter((repo: RepositoryResponse) => {
    if (!searchQuery.trim()) return true
    const q = searchQuery.toLowerCase()
    return (
      repo.fullName.toLowerCase().includes(q) ||
      repo.defaultBranch.toLowerCase().includes(q) ||
      repo.externalId.toLowerCase().includes(q)
    )
  })

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
      {/* Breadcrumb Navigation */}
      <div className="flex items-center gap-2 text-xs text-slate-400">
        <Link to="/organizations" className="hover:text-slate-200">
          Organizations
        </Link>
        <span>/</span>
        <Link
          to={`/organizations/${resolvedOrgId}`}
          className="hover:text-slate-200 font-medium text-slate-300"
        >
          {currentOrg?.name || 'Organization'}
        </Link>
        <span>/</span>
        <span className="text-slate-100 font-medium">Repositories</span>
      </div>

      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-blue-600/10 border border-blue-500/20 flex items-center justify-center text-blue-400">
              <FolderGit2 className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-bold text-white tracking-tight">Connected Repositories</h1>
                {currentOrg && (
                  <Badge variant="neutral" className="font-mono text-xs">
                    {currentOrg.slug}
                  </Badge>
                )}
                {currentUserRole && (
                  <Badge variant="purple" className="text-[11px]">
                    Role: {currentUserRole}
                  </Badge>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-1">
                GitHub repositories connected to {currentOrg?.name || 'this organization'} for AI engineering workflows.
              </p>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2.5">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsCreateOpen(true)}
            title={!canManageRepos && currentUserRole ? 'Requires Owner or Admin role' : undefined}
          >
            <Plus className="h-3.5 w-3.5 mr-1.5" />
            Register Repository
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsConnectOpen(true)}
            title={!canManageRepos && currentUserRole ? 'Requires Owner or Admin role' : undefined}
          >
            <Link2 className="h-3.5 w-3.5 mr-1.5" />
            Connect GitHub
          </Button>
        </div>
      </div>

      {/* Permission Warning for Members/Viewers */}
      {currentUserRole && !canManageRepos && (
        <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/20 text-xs text-amber-300 flex items-center gap-2.5">
          <ShieldAlert className="h-4 w-4 text-amber-400 shrink-0" />
          <span>
            You are a <strong>{currentUserRole}</strong> in this organization. Only Owners and Admins have permission to connect or register repositories.
          </span>
        </div>
      )}

      {/* Toolbar / Search */}
      <div className="flex items-center justify-between gap-4">
        <div className="relative w-full max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-500" />
          <Input
            placeholder="Filter repositories..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-8 text-xs h-9 bg-slate-900/60"
          />
        </div>

        {repositoriesPage && (
          <span className="text-xs text-slate-400">
            Total Repositories:{' '}
            <strong className="text-slate-200 font-semibold">
              {repositoriesPage.totalElements}
            </strong>
          </span>
        )}
      </div>

      {/* Content State Handling */}
      {isLoading ? (
        <div className="py-16">
          <LoadingSpinner size="lg" label="Loading repositories from backend..." />
        </div>
      ) : isError ? (
        <ErrorMessage
          title="Failed to load repositories"
          message={error instanceof Error ? error.message : 'An error occurred while fetching repositories.'}
          onRetry={() => refetch()}
        />
      ) : filteredRepositories.length === 0 ? (
        allRepositories.length === 0 ? (
          <EmptyState
            title="No repositories connected"
            description="Connect a GitHub repository via the DevFlow GitHub App or register an existing repository to start running automated AI tasks."
            icon={<FolderGit2 className="h-8 w-8 text-slate-500" />}
          >
            <div className="flex items-center gap-3">
              <Button variant="outline" size="sm" onClick={() => setIsCreateOpen(true)}>
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                Register Repository
              </Button>
              <Button variant="primary" size="sm" onClick={() => setIsConnectOpen(true)}>
                <Link2 className="h-3.5 w-3.5 mr-1.5" />
                Connect GitHub
              </Button>
            </div>
          </EmptyState>
        ) : (
          <EmptyState
            title="No matching repositories"
            description={`No repositories match your filter "${searchQuery}".`}
            icon={<Search className="h-6 w-6 text-slate-500" />}
          >
            <Button variant="ghost" size="sm" onClick={() => setSearchQuery('')}>
              Clear Filter
            </Button>
          </EmptyState>
        )
      ) : (
        /* Repository Cards / Grid */
        <div className="space-y-3">
          {filteredRepositories.map((repo) => {
            const safeExternalUrl = getSafeGithubUrl(repo.fullName)
            return (
              <div
                key={repo.id}
                className="group relative p-4 rounded-xl border border-slate-800/80 bg-slate-900/40 hover:bg-slate-900/80 hover:border-slate-700/80 transition-all duration-200 flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="flex items-start md:items-center gap-3.5 min-w-0">
                  <div className="h-9 w-9 rounded-lg bg-slate-800/90 border border-slate-700/60 flex items-center justify-center text-white shrink-0 mt-0.5 md:mt-0">
                    <GithubIcon className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 space-y-1">
                    <div className="flex items-center gap-2.5 flex-wrap">
                      <Link
                        to={`/organizations/${resolvedOrgId}/repositories/${repo.id}`}
                        className="text-sm font-semibold text-white hover:text-blue-400 transition-colors truncate"
                      >
                        {repo.fullName}
                      </Link>
                      <Badge
                        variant={repo.active ? 'success' : 'neutral'}
                        className="text-[10px] py-0 px-2 uppercase tracking-wide"
                      >
                        {repo.active ? 'Active' : 'Inactive'}
                      </Badge>
                      <span className="flex items-center gap-1 text-[11px] font-mono text-slate-400 bg-slate-800/80 px-2 py-0.5 rounded border border-slate-700/40">
                        <GitBranch className="h-3 w-3 text-slate-500" />
                        {repo.defaultBranch}
                      </span>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-slate-400 flex-wrap">
                      <span className="text-[11px] font-mono text-slate-500">
                        ext: {repo.externalId}
                      </span>
                      <span>•</span>
                      <span>
                        Added {new Date(repo.createdAt).toLocaleDateString(undefined, {
                          month: 'short',
                          day: 'numeric',
                          year: 'numeric',
                        })}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0 self-end md:self-center">
                  {safeExternalUrl && (
                    <a
                      href={safeExternalUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                      title="Open on GitHub"
                      aria-label={`Open ${repo.fullName} on GitHub`}
                    >
                      <ExternalLink className="h-3.5 w-3.5" />
                    </a>
                  )}
                  <Link to={`/organizations/${resolvedOrgId}/repositories/${repo.id}`}>
                    <Button variant="ghost" size="sm" className="gap-1 text-slate-300 hover:text-white">
                      View Details
                      <span aria-hidden="true">→</span>
                    </Button>
                  </Link>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Pagination Controls */}
      {repositoriesPage && repositoriesPage.totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-slate-800/80 pt-4 text-xs text-slate-400">
          <span>
            Page {repositoriesPage.page + 1} of {repositoriesPage.totalPages}
          </span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={repositoriesPage.first}
              className="gap-1"
            >
              <ChevronLeft className="h-3 w-3" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(repositoriesPage.totalPages - 1, p + 1))}
              disabled={repositoriesPage.last}
              className="gap-1"
            >
              Next
              <ChevronRight className="h-3 w-3" />
            </Button>
          </div>
        </div>
      )}

      {/* Modals */}
      <CreateRepositoryDialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        organizationId={resolvedOrgId}
      />

      <ConnectGithubDialog
        isOpen={isConnectOpen}
        onClose={() => setIsConnectOpen(false)}
        organizationId={resolvedOrgId}
        organizationSlug={currentOrg?.slug}
      />
    </div>
  )
}
