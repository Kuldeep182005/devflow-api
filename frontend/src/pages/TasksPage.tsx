import React, { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  CheckSquare2,
  Plus,
  Building2,
  FolderGit2,
  ChevronLeft,
  ChevronRight,
  Filter,
  AlertTriangle,
  ArrowRight,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Select } from '@/components/ui/select'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { EmptyState } from '@/components/common/EmptyState'
import { TaskStatusBadge } from '@/components/tasks/TaskStatusBadge'
import { CreateTaskDialog } from '@/components/tasks/CreateTaskDialog'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { useAuth } from '@/hooks/useAuth'
import { tasksApi } from '@/api/tasks'
import { repositoriesApi } from '@/api/repositories'
import { organizationsApi } from '@/api/organizations'
import type { TaskResponse, RepositoryResponse } from '@/types/api'

export const TasksPage: React.FC = () => {
  const params = useParams<{ organizationId?: string }>()
  const navigate = useNavigate()
  const { activeOrganization, organizations } = useActiveOrganization()
  const { session } = useAuth()

  // Resolve target organization
  const resolvedOrgId = params.organizationId || activeOrganization?.id || null
  const currentOrg = organizations.find((o) => o.id === resolvedOrgId) || activeOrganization

  // Query & pagination state
  const [page, setPage] = useState(0)
  const pageSize = 15
  const [selectedRepoId, setSelectedRepoId] = useState<string>('')
  const [isCreateOpen, setIsCreateOpen] = useState(false)

  // Query organization repositories for filter & name resolution
  const { data: repositoriesData } = useQuery({
    queryKey: ['organizations', resolvedOrgId, 'repositories', { size: 100 }],
    queryFn: () => repositoriesApi.list(resolvedOrgId!, { size: 100 }),
    enabled: !!resolvedOrgId,
  })

  // Query tasks with backend pagination and repository filtering
  const {
    data: tasksPage,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: [
      'organizations',
      resolvedOrgId,
      'tasks',
      { repositoryId: selectedRepoId || undefined, page, size: pageSize },
    ],
    queryFn: () =>
      tasksApi.list(resolvedOrgId!, {
        repositoryId: selectedRepoId || undefined,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      }),
    enabled: !!resolvedOrgId,
  })

  // Query organization members to determine current user's role
  const { data: membersPage } = useQuery({
    queryKey: ['organizations', resolvedOrgId, 'members'],
    queryFn: () => organizationsApi.listMembers(resolvedOrgId!),
    enabled: !!resolvedOrgId,
  })

  const currentMember = membersPage?.content?.find((m) => m.userId === session?.userId)
  const currentUserRole = currentMember?.role
  // VIEWER can inspect tasks, while OWNER and ADMIN can dispatch/manage
  const canDispatchTasks = currentUserRole !== 'VIEWER'

  // If no organization is selected anywhere
  if (!resolvedOrgId) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full space-y-6">
        <div className="border-b border-slate-800/80 pb-4">
          <h1 className="text-xl font-bold text-white tracking-tight">AI Tasks</h1>
          <p className="text-xs text-slate-400 mt-1">
            Asynchronous code generation and bug fix tasks dispatched through the task state machine.
          </p>
        </div>

        <EmptyState
          title="No Organization Selected"
          description="Select an organization from the workspace switcher or create one to view and dispatch engineering tasks."
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

  const tasks = tasksPage?.content || []
  const totalPages = tasksPage?.totalPages || 0
  const totalElements = tasksPage?.totalElements || 0

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
        <span className="text-slate-100 font-medium">Tasks</span>
      </div>

      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-blue-600/10 border border-blue-500/20 flex items-center justify-center text-blue-400">
              <CheckSquare2 className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-bold text-white tracking-tight">AI Tasks</h1>
                <Badge variant="neutral" className="text-slate-400 font-mono text-[10px]">
                  {totalElements} total
                </Badge>
              </div>
              <p className="text-xs text-slate-400 mt-1">
                Autonomous code generation, debugging, and patch verification pipelines.
              </p>
            </div>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-3">
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsCreateOpen(true)}
            disabled={!canDispatchTasks}
            title={!canDispatchTasks ? 'Viewer role cannot dispatch tasks' : undefined}
          >
            <Plus className="h-3.5 w-3.5 mr-1.5" />
            Dispatch Task
          </Button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#0d121c] p-3.5 rounded-xl border border-slate-800/80">
        <div className="flex items-center gap-2 max-w-sm w-full">
          <Filter className="h-4 w-4 text-slate-400 shrink-0 ml-1" />
          <Select
            value={selectedRepoId}
            onChange={(e) => {
              setSelectedRepoId(e.target.value)
              setPage(0) // Reset to first page upon filter change
            }}
            className="h-8.5 text-xs bg-slate-900 border-slate-800"
            aria-label="Filter tasks by repository"
          >
            <option value="">All Connected Repositories</option>
            {repositories.map((repo) => (
              <option key={repo.id} value={repo.id}>
                {repo.fullName}
              </option>
            ))}
          </Select>
        </div>

        {selectedRepoId && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSelectedRepoId('')
              setPage(0)
            }}
            className="text-xs text-slate-400 hover:text-white"
          >
            Clear Filter
          </Button>
        )}
      </div>

      {/* Tasks Table / Content */}
      {isLoading ? (
        <div className="p-16 flex flex-col items-center justify-center space-y-3">
          <LoadingSpinner size="lg" />
          <p className="text-xs text-slate-400">Loading task records from backend...</p>
        </div>
      ) : isError ? (
        <ErrorMessage
          title="Failed to Load Tasks"
          message={error instanceof Error ? error.message : 'Could not retrieve tasks.'}
          onRetry={() => refetch()}
        />
      ) : tasks.length === 0 ? (
        <EmptyState
          title={selectedRepoId ? 'No Tasks for this Repository' : 'No Tasks Dispatched Yet'}
          description={
            selectedRepoId
              ? 'No engineering tasks have been dispatched for the selected repository.'
              : 'Dispatch your first AI engineering task to initiate autonomous agent analysis and sandbox verification.'
          }
          icon={<CheckSquare2 className="h-8 w-8 text-slate-500" />}
        >
          {canDispatchTasks && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => setIsCreateOpen(true)}
            >
              <Plus className="h-3.5 w-3.5 mr-1.5" />
              Dispatch Task
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="space-y-4">
          <div className="overflow-hidden rounded-xl border border-slate-800/80 bg-[#0e131d] shadow-xs">
            <table className="w-full text-left border-collapse" aria-label="Tasks List">
              <thead>
                <tr className="border-b border-slate-800/80 bg-slate-900/60 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                  <th scope="col" className="py-3 px-4">Task Details</th>
                  <th scope="col" className="py-3 px-4">Repository</th>
                  <th scope="col" className="py-3 px-4">Status</th>
                  <th scope="col" className="py-3 px-4">Source</th>
                  <th scope="col" className="py-3 px-4">Created</th>
                  <th scope="col" className="py-3 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-xs">
                {tasks.map((task: TaskResponse) => {
                  const repo = repoMap.get(task.repositoryId)
                  const repoName = repo ? repo.fullName : `repo:${task.repositoryId.slice(0, 8)}`
                  const createdDate = new Date(task.createdAt).toLocaleString(undefined, {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })

                  return (
                    <tr
                      key={task.id}
                      onClick={() =>
                        navigate(`/organizations/${resolvedOrgId}/tasks/${task.id}`)
                      }
                      className="hover:bg-slate-900/60 cursor-pointer transition-colors group"
                    >
                      {/* Title & Description */}
                      <td className="py-3.5 px-4 max-w-md">
                        <div className="flex flex-col">
                          <span className="font-semibold text-slate-100 group-hover:text-blue-400 transition-colors line-clamp-1">
                            {task.title}
                          </span>
                          {task.description && (
                            <span className="text-[11px] text-slate-400 line-clamp-1 mt-0.5">
                              {task.description}
                            </span>
                          )}
                          {task.failureCode && (
                            <span className="text-[10px] text-red-400 flex items-center gap-1 mt-1 font-mono">
                              <AlertTriangle className="h-3 w-3 shrink-0" />
                              {task.failureCode}
                            </span>
                          )}
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
                        <TaskStatusBadge status={task.status} />
                      </td>

                      {/* Source */}
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        <Badge variant="neutral" className="text-[10px] font-mono">
                          {task.sourceType}
                        </Badge>
                      </td>

                      {/* Created */}
                      <td className="py-3.5 px-4 whitespace-nowrap text-slate-400 font-mono text-[11px]">
                        {createdDate}
                      </td>

                      {/* Action */}
                      <td className="py-3.5 px-4 whitespace-nowrap text-right">
                        <span className="inline-flex items-center gap-1 text-[11px] font-medium text-slate-400 group-hover:text-blue-400 transition-colors">
                          View
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
                {totalElements} total tasks)
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

      {/* Task Creation Dialog */}
      <CreateTaskDialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        organizationId={resolvedOrgId}
        defaultRepositoryId={selectedRepoId || undefined}
        onSuccess={(newTask) => {
          navigate(`/organizations/${resolvedOrgId}/tasks/${newTask.id}`)
        }}
      />
    </div>
  )
}
