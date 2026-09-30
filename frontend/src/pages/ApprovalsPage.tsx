import React, { useState } from 'react'
import { useParams, useSearchParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  ShieldCheck,
  Building2,
  ChevronLeft,
  ChevronRight,
  CheckSquare2,
  FolderGit2,
  Hourglass,
  ArrowRight,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { EmptyState } from '@/components/common/EmptyState'
import { ApprovalStatusBadge } from '@/components/approvals/ApprovalStatusBadge'
import { SandboxStatusBadge } from '@/components/approvals/SandboxStatusBadge'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { approvalsApi } from '@/api/approvals'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import type { ApprovalResponse, RepositoryResponse, TaskResponse } from '@/types/api'

export const ApprovalsPage: React.FC = () => {
  const params = useParams<{ organizationId?: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const { activeOrganization, organizations } = useActiveOrganization()

  const resolvedOrgId = params.organizationId || activeOrganization?.id || null
  const currentOrg = organizations.find((o) => o.id === resolvedOrgId) || activeOrganization

  // URL query parameter for filtering by taskId
  const filterTaskId = searchParams.get('taskId') || ''

  // Pagination state
  const [page, setPage] = useState(0)
  const pageSize = 15
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING' | 'DECIDED'>('ALL')

  // Query approvals with backend pagination & optional taskId filter
  const {
    data: approvalsPage,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: [
      'organizations',
      resolvedOrgId,
      'approvals',
      { taskId: filterTaskId || undefined, page, size: pageSize },
    ],
    queryFn: () =>
      approvalsApi.list(resolvedOrgId!, {
        taskId: filterTaskId || undefined,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      }),
    enabled: !!resolvedOrgId,
    // Bounded polling while on page to capture newly created approval gates
    refetchInterval: 5000,
  })

  // Query repositories to map repo names
  const { data: repositoriesData } = useQuery({
    queryKey: ['organizations', resolvedOrgId, 'repositories', { size: 100 }],
    queryFn: () => repositoriesApi.list(resolvedOrgId!, { size: 100 }),
    enabled: !!resolvedOrgId,
  })

  // Query tasks to map task titles
  const { data: tasksData } = useQuery({
    queryKey: ['organizations', resolvedOrgId, 'tasks', { size: 100 }],
    queryFn: () => tasksApi.list(resolvedOrgId!, { size: 100 }),
    enabled: !!resolvedOrgId,
  })

  if (!resolvedOrgId) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full space-y-6">
        <div className="border-b border-slate-800/80 pb-4">
          <h1 className="text-xl font-bold text-white tracking-tight">Human Approval Queue</h1>
          <p className="text-xs text-slate-400 mt-1">
            Review and authorize validated proposed changes before consequential GitHub branches or pull requests are executed.
          </p>
        </div>

        <EmptyState
          title="No Organization Selected"
          description="Select an organization from the workspace switcher to view pending and decided approval requests."
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

  const repositories = repositoriesData?.content || []
  const repoMap = new Map<string, RepositoryResponse>()
  repositories.forEach((repo) => repoMap.set(repo.id, repo))

  const tasks = tasksData?.content || []
  const taskMap = new Map<string, TaskResponse>()
  tasks.forEach((t) => taskMap.set(t.id, t))

  const allApprovals = approvalsPage?.content || []
  const filteredApprovals = allApprovals.filter((approval: ApprovalResponse) => {
    if (statusFilter === 'PENDING') return approval.status === 'PENDING'
    if (statusFilter === 'DECIDED') return approval.status !== 'PENDING'
    return true
  })

  const pendingCount = allApprovals.filter((a) => a.status === 'PENDING').length
  const totalPages = approvalsPage?.totalPages || 0
  const totalElements = approvalsPage?.totalElements || 0

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
      {/* Breadcrumbs */}
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
        <span className="text-slate-100 font-medium">Approvals</span>
      </div>

      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-purple-600/10 border border-purple-500/20 flex items-center justify-center text-purple-400">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-bold text-white tracking-tight">Human Approval Queue</h1>
                <Badge variant="neutral" className="text-slate-400 font-mono text-[10px]">
                  {totalElements} total
                </Badge>
                {pendingCount > 0 && (
                  <Badge variant="purple" className="text-[10px] animate-pulse">
                    <Hourglass className="h-2.5 w-2.5 mr-1" />
                    {pendingCount} Pending Review
                  </Badge>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-1">
                Consequential change authorization gate: human approval required before remote GitHub mutations.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Filter and Status Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#0d121c] p-3.5 rounded-xl border border-slate-800/80">
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setStatusFilter('ALL')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              statusFilter === 'ALL'
                ? 'bg-slate-800 text-white shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            All Approvals
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('PENDING')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors flex items-center gap-1.5 ${
              statusFilter === 'PENDING'
                ? 'bg-purple-900/50 text-purple-200 border border-purple-700/50 shadow-xs'
                : 'text-slate-400 hover:text-purple-300'
            }`}
          >
            <Hourglass className="h-3 w-3" />
            Pending Review
            {pendingCount > 0 && (
              <span className="h-4 px-1 rounded-full bg-purple-500/20 text-[10px] text-purple-300">
                {pendingCount}
              </span>
            )}
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('DECIDED')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              statusFilter === 'DECIDED'
                ? 'bg-slate-800 text-white shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Decided
          </button>
        </div>

        {filterTaskId && (
          <div className="flex items-center gap-2 text-xs text-slate-300 bg-slate-900 px-3 py-1 rounded-lg border border-slate-800">
            <span className="text-slate-400 font-mono text-[11px]">
              Task: {filterTaskId.slice(0, 8)}...
            </span>
            <button
              onClick={() => {
                searchParams.delete('taskId')
                setSearchParams(searchParams)
              }}
              className="text-[11px] text-blue-400 hover:text-blue-300 ml-1"
            >
              Clear
            </button>
          </div>
        )}
      </div>

      {/* Approvals Table */}
      {isLoading ? (
        <div className="p-16 flex flex-col items-center justify-center space-y-3">
          <LoadingSpinner size="lg" />
          <p className="text-xs text-slate-400">Loading approval queue from backend...</p>
        </div>
      ) : isError ? (
        <ErrorMessage
          title="Failed to Load Approvals"
          message={error instanceof Error ? error.message : 'Could not retrieve approval requests.'}
          onRetry={() => refetch()}
        />
      ) : filteredApprovals.length === 0 ? (
        <EmptyState
          title={statusFilter === 'PENDING' ? 'No Pending Approvals' : 'No Approvals Found'}
          description={
            statusFilter === 'PENDING'
              ? 'There are currently no change proposals awaiting human authorization in this organization.'
              : 'When AI agents complete task analysis and sandbox verification, proposals requiring review will appear here.'
          }
          icon={<ShieldCheck className="h-8 w-8 text-slate-500" />}
        />
      ) : (
        <div className="space-y-4">
          <div className="overflow-hidden rounded-xl border border-slate-800/80 bg-[#0e131d] shadow-xs">
            <table className="w-full text-left border-collapse" aria-label="Approvals Queue">
              <thead>
                <tr className="border-b border-slate-800/80 bg-slate-900/60 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                  <th scope="col" className="py-3 px-4">Task & Proposal</th>
                  <th scope="col" className="py-3 px-4">Repository</th>
                  <th scope="col" className="py-3 px-4">Status</th>
                  <th scope="col" className="py-3 px-4">Sandbox Check</th>
                  <th scope="col" className="py-3 px-4">Requested</th>
                  <th scope="col" className="py-3 px-4 text-right">Review</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-xs">
                {filteredApprovals.map((approval: ApprovalResponse) => {
                  const repo = repoMap.get(approval.repositoryId)
                  const task = taskMap.get(approval.taskId)
                  const repoName = repo ? repo.fullName : `repo:${approval.repositoryId.slice(0, 8)}`
                  const taskTitle = task ? task.title : `Task ${approval.taskId.slice(0, 8)}...`
                  const requestedDate = new Date(approval.requestedAt).toLocaleString(undefined, {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })

                  const isPending = approval.status === 'PENDING'

                  return (
                    <tr
                      key={approval.id}
                      onClick={() =>
                        navigate(`/organizations/${resolvedOrgId}/approvals/${approval.id}`)
                      }
                      className={`hover:bg-slate-900/60 cursor-pointer transition-colors group ${
                        isPending ? 'bg-purple-950/10' : ''
                      }`}
                    >
                      {/* Task & Proposal details */}
                      <td className="py-3.5 px-4 max-w-md">
                        <div className="flex flex-col">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-100 group-hover:text-purple-400 transition-colors line-clamp-1">
                              {taskTitle}
                            </span>
                            <Badge variant="neutral" className="text-[10px] font-mono shrink-0">
                              v{approval.proposedChangeVersion}
                            </Badge>
                          </div>
                          <div className="flex items-center gap-2 mt-1 text-[11px] text-slate-400">
                            <CheckSquare2 className="h-3 w-3 shrink-0" />
                            <span className="font-mono text-[10px] truncate max-w-[180px]">
                              task:{approval.taskId.slice(0, 8)}
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* Repository */}
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-slate-300">
                          <FolderGit2 className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                          <span className="truncate max-w-[160px] font-mono text-[11px]">
                            {repoName}
                          </span>
                        </div>
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        <ApprovalStatusBadge status={approval.status} />
                      </td>

                      {/* Sandbox Execution */}
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        <SandboxStatusBadge status={approval.sandboxExecutionStatus} />
                      </td>

                      {/* Requested Date */}
                      <td className="py-3.5 px-4 whitespace-nowrap text-slate-400 font-mono text-[11px]">
                        {requestedDate}
                      </td>

                      {/* Action */}
                      <td className="py-3.5 px-4 whitespace-nowrap text-right">
                        <span className="inline-flex items-center gap-1 text-[11px] font-medium text-slate-400 group-hover:text-purple-400 transition-colors">
                          {isPending ? 'Review' : 'View'}
                          <ArrowRight className="h-3 w-3" />
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-800/80 pt-4 text-xs text-slate-400">
              <div>
                Showing page <span className="font-semibold text-slate-200">{page + 1}</span> of{' '}
                <span className="font-semibold text-slate-200">{totalPages}</span> (
                {totalElements} total records)
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="h-8 px-2.5 text-xs"
                >
                  <ChevronLeft className="h-3.5 w-3.5 mr-1" />
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="h-8 px-2.5 text-xs"
                >
                  Next
                  <ChevronRight className="h-3.5 w-3.5 ml-1" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
