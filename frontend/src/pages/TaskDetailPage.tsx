import React, { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  FolderGit2,
  GitBranch,
  Clock,
  Ban,
  AlertTriangle,
  ChevronLeft,
  Hourglass,
  CheckCircle2,
  Cpu,
  ArrowRight,
  Boxes,
  FileCode2,
  Sparkles,
  ExternalLink,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Dialog } from '@/components/ui/dialog'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { TaskStatusBadge } from '@/components/tasks/TaskStatusBadge'
import { TaskLifecycle } from '@/components/tasks/TaskLifecycle'
import { TaskEventTimeline } from '@/components/tasks/TaskEventTimeline'
import { GithubMutationStatusBadge } from '@/components/github/GithubMutationStatusBadge'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { useAuth } from '@/hooks/useAuth'
import { tasksApi } from '@/api/tasks'
import { repositoriesApi } from '@/api/repositories'
import { approvalsApi } from '@/api/approvals'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'
import { getSafeGithubPrUrl } from '@/lib/urls'
import type { TaskResponse, TaskStatus, GithubMutationResponse } from '@/types/api'

// Helper to determine if task is terminal (stops polling)
function isTerminalStatus(status?: TaskStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED'
}

export const TaskDetailPage: React.FC = () => {
  const params = useParams<{ organizationId: string; taskId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { session } = useAuth()
  const { organizations, activeOrganization } = useActiveOrganization()

  const organizationId = params.organizationId || activeOrganization?.id || ''
  const taskId = params.taskId || ''

  const currentOrg = organizations.find((o) => o.id === organizationId) || activeOrganization

  // Cancellation modal state
  const [isCancelDialogOpen, setIsCancelDialogOpen] = useState(false)
  const [cancelError, setCancelError] = useState<{
    message: string
    code?: string
    requestId?: string
  } | null>(null)

  // Query organization members to determine current user's role
  const { data: membersPage } = useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: () => organizationsApi.listMembers(organizationId),
    enabled: !!organizationId,
  })

  const currentMember = membersPage?.content?.find((m) => m.userId === session?.userId)
  const currentUserRole = currentMember?.role
  // Viewer cannot cancel tasks
  const canCancelTask = currentUserRole !== 'VIEWER'

  // Query Task details with bounded polling while active
  const {
    data: task,
    isLoading: isLoadingTask,
    isError: isTaskError,
    error: taskError,
    refetch: refetchTask,
  } = useQuery({
    queryKey: ['organizations', organizationId, 'tasks', taskId],
    queryFn: () => tasksApi.get(organizationId, taskId),
    enabled: !!organizationId && !!taskId,
    // Bounded polling: poll every 3s only while task is in active state; stop immediately when terminal
    refetchInterval: (query) => {
      const data = query.state.data as TaskResponse | undefined
      if (!data || isTerminalStatus(data.status)) {
        return false
      }
      return 3000
    },
  })

  // Query Task events with bounded polling aligned with task state
  const { data: eventsPage, isLoading: isLoadingEvents } = useQuery({
    queryKey: ['organizations', organizationId, 'tasks', taskId, 'events'],
    queryFn: () => tasksApi.events(organizationId, taskId, { size: 100 }),
    enabled: !!organizationId && !!taskId,
    refetchInterval: () => {
      if (!task || isTerminalStatus(task.status)) {
        return false
      }
      return 3000
    },
  })

  // Query Repository information
  const { data: repository } = useQuery({
    queryKey: ['organizations', organizationId, 'repositories', task?.repositoryId],
    queryFn: () => repositoriesApi.get(organizationId, task!.repositoryId),
    enabled: !!organizationId && !!task?.repositoryId,
  })

  // Query Approval associated with this task (real backend downstream state)
  const { data: approvalsPage } = useQuery({
    queryKey: ['organizations', organizationId, 'approvals', { taskId }],
    queryFn: () => approvalsApi.list(organizationId, { taskId }),
    enabled: !!organizationId && !!taskId,
  })

  const approval = approvalsPage?.content?.[0]

  // Query GitHub mutation if approval has been approved (authoritative downstream delivery)
  const { data: mutation } = useQuery({
    queryKey: ['organizations', organizationId, 'approvals', approval?.id, 'mutation'],
    queryFn: () => approvalsApi.getMutation(organizationId, approval!.id),
    enabled: !!organizationId && !!approval?.id && approval.status === 'APPROVED',
    refetchInterval: (query) => {
      const data = query.state.data as GithubMutationResponse | undefined
      if (!data || data.status === 'SUCCEEDED' || data.status === 'FAILED') {
        return false
      }
      return 3000
    },
  })

  // Cancel task mutation
  const cancelMutation = useMutation({
    mutationFn: () => {
      const idempotencyKey = `cancel-task-${taskId}-${Date.now()}`
      return tasksApi.cancel(organizationId, taskId, idempotencyKey)
    },
    onSuccess: (updatedTask) => {
      setIsCancelDialogOpen(false)
      setCancelError(null)
      queryClient.setQueryData(
        ['organizations', organizationId, 'tasks', taskId],
        updatedTask
      )
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks'],
      })
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'tasks', taskId, 'events'],
      })
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.status === 403) {
          setCancelError({
            message: 'You do not have permission to cancel this task.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }
        if (err.status === 409) {
          setCancelError({
            message: 'This task has already reached a terminal state and cannot be cancelled.',
            code: err.code,
            requestId: err.requestId,
          })
          return
        }
        setCancelError({
          message: err.message || 'Failed to cancel task.',
          code: err.code,
          requestId: err.requestId,
        })
      } else {
        setCancelError({
          message: 'An unexpected network error occurred while cancelling the task.',
        })
      }
    },
  })

  if (isLoadingTask) {
    return (
      <div className="p-16 flex flex-col items-center justify-center space-y-3">
        <LoadingSpinner size="lg" />
        <p className="text-xs text-slate-400">Loading task details from backend...</p>
      </div>
    )
  }

  if (isTaskError || !task) {
    const errorObj = taskError instanceof ApiClientError ? taskError : null
    return (
      <div className="p-8 max-w-5xl mx-auto w-full space-y-6">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate(`/organizations/${organizationId}/tasks`)}
        >
          <ChevronLeft className="h-4 w-4 mr-1" />
          Back to Tasks
        </Button>
        <ErrorMessage
          title="Task Not Found"
          message={
            errorObj?.status === 404
              ? 'The requested task does not exist or you do not have permission to view it.'
              : errorObj?.message || 'Failed to load task details.'
          }
          code={errorObj?.code}
          requestId={errorObj?.requestId}
          onRetry={() => refetchTask()}
        />
      </div>
    )
  }

  const isTerminal = isTerminalStatus(task.status)
  const events = eventsPage?.content || []

  // Check if PR events exist
  const hasPrCreated = events.some((e) => e.eventType === 'GITHUB_PR_CREATED')
  const hasBranchCreated = events.some((e) => e.eventType === 'GITHUB_BRANCH_CREATED')
  const safePrUrl = getSafeGithubPrUrl(mutation?.pullRequestUrl)

  const createdAtFormatted = new Date(task.createdAt).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })

  const startedAtFormatted = task.startedAt
    ? new Date(task.startedAt).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    : null

  const finishedAtFormatted = task.finishedAt
    ? new Date(task.finishedAt).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    : null

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
      {/* Breadcrumb Navigation */}
      <div className="flex items-center gap-2 text-xs text-slate-400">
        <Link to="/organizations" className="hover:text-slate-200">
          Organizations
        </Link>
        <span>/</span>
        <Link
          to={`/organizations/${organizationId}`}
          className="hover:text-slate-200 font-medium text-slate-300"
        >
          {currentOrg?.name || 'Organization'}
        </Link>
        <span>/</span>
        <Link
          to={`/organizations/${organizationId}/tasks`}
          className="hover:text-slate-200 font-medium text-slate-300"
        >
          Tasks
        </Link>
        <span>/</span>
        <span className="text-slate-100 font-mono truncate max-w-[200px]">
          {task.id.slice(0, 8)}...
        </span>
      </div>

      {/* Main Task Header */}
      <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-6 shadow-xs">
        <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-xl font-bold text-white tracking-tight">{task.title}</h1>
              <TaskStatusBadge status={task.status} />
              {!isTerminal && (
                <span className="inline-flex items-center gap-1.5 text-[11px] text-blue-400 bg-blue-950/40 px-2 py-0.5 rounded-full border border-blue-500/30">
                  <span className="h-1.5 w-1.5 rounded-full bg-blue-400 animate-ping" />
                  Live Sync (3s)
                </span>
              )}
            </div>

            {task.description && (
              <p className="text-xs text-slate-300 leading-relaxed max-w-3xl">
                {task.description}
              </p>
            )}

            <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400 pt-1">
              <div className="flex items-center gap-1.5">
                <FolderGit2 className="h-3.5 w-3.5 text-slate-400" />
                {repository ? (
                  <Link
                    to={`/organizations/${organizationId}/repositories/${repository.id}`}
                    className="hover:text-blue-400 text-slate-300 font-mono"
                  >
                    {repository.fullName}
                  </Link>
                ) : (
                  <span className="font-mono text-slate-400">repo:{task.repositoryId.slice(0, 8)}</span>
                )}
              </div>

              {task.branchName && (
                <div className="flex items-center gap-1.5 font-mono text-slate-300">
                  <GitBranch className="h-3.5 w-3.5 text-slate-400" />
                  <span>{task.branchName}</span>
                </div>
              )}

              <div className="flex items-center gap-1.5 font-mono">
                <Clock className="h-3.5 w-3.5 text-slate-400" />
                <span>Created {createdAtFormatted}</span>
              </div>
            </div>
          </div>

          {/* Action Header Controls */}
          <div className="flex items-center gap-3 shrink-0">
            {!isTerminal && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setCancelError(null)
                  setIsCancelDialogOpen(true)
                }}
                disabled={!canCancelTask}
                title={!canCancelTask ? 'Viewer role cannot cancel tasks' : 'Cancel this active task'}
                className="border-red-500/30 text-red-400 hover:bg-red-950/30 hover:text-red-300"
              >
                <Ban className="h-3.5 w-3.5 mr-1.5" />
                Cancel Task
              </Button>
            )}
          </div>
        </div>

        {/* Prominent Failure Alert if Task Failed */}
        {task.status === 'FAILED' && (
          <div className="mt-4 p-4 rounded-lg border border-red-500/40 bg-red-950/20 text-red-300 space-y-1.5">
            <div className="flex items-center gap-2 font-semibold text-xs text-red-200">
              <AlertTriangle className="h-4 w-4 text-red-400 shrink-0" />
              <span>Task Execution Failed</span>
              {task.failureCode && (
                <Badge variant="danger" className="text-[10px] font-mono ml-auto">
                  {task.failureCode}
                </Badge>
              )}
            </div>
            {task.failureMessage && (
              <p className="text-xs text-red-200/90 pl-6 leading-relaxed font-mono">
                {task.failureMessage}
              </p>
            )}
            {task.failedAt && (
              <p className="text-[10px] text-red-400/80 pl-6 font-mono">
                Failed at: {new Date(task.failedAt).toLocaleString()}
              </p>
            )}
          </div>
        )}
      </div>

      {/* Visual State Machine Lifecycle */}
      <TaskLifecycle status={task.status} />

      {/* Downstream AI Workflow & Approvals Gateway (Real Backend State) */}
      <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-lg bg-purple-600/10 border border-purple-500/20 flex items-center justify-center text-purple-400">
              <Sparkles className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                AI Engineering Workflow
              </h2>
              <p className="text-[11px] text-slate-400 mt-0.5">
                Downstream agent orchestration, isolated sandbox telemetry, and approval status
              </p>
            </div>
          </div>
          <Badge variant="neutral" className="text-[10px] font-mono text-slate-400">
            Authoritative Backend State
          </Badge>
        </div>

        {/* Status Callout depending on real state */}
        {task.status === 'WAITING_FOR_APPROVAL' ? (
          <div className="p-4 rounded-lg border border-purple-500/40 bg-purple-950/20 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Hourglass className="h-4 w-4 text-purple-400 animate-pulse shrink-0" />
                <span className="text-xs font-bold text-purple-200">
                  Human Approval Required
                </span>
              </div>
              <p className="text-xs text-purple-300/80 leading-relaxed">
                The autonomous worker has proposed code modifications and passed isolated sandbox verification.
                A human reviewer must authorize the proposal before the branch and pull request are executed on GitHub.
              </p>
            </div>

            {approval && (
              <Link to={`/organizations/${organizationId}/approvals/${approval.id}`}>
                <Button variant="primary" size="sm" className="shrink-0 bg-purple-600 hover:bg-purple-500">
                  Review Approval
                  <ArrowRight className="h-3.5 w-3.5 ml-1.5" />
                </Button>
              </Link>
            )}
          </div>
        ) : task.status === 'RUNNING' ? (
          <div className="p-4 rounded-lg border border-blue-500/30 bg-blue-950/20 flex items-center gap-3">
            <Cpu className="h-5 w-5 text-blue-400 animate-pulse shrink-0" />
            <div className="space-y-0.5">
              <span className="text-xs font-semibold text-blue-200">
                AI Worker In Progress
              </span>
              <p className="text-xs text-blue-300/70">
                Worker dispatched. The agent is analyzing the codebase, formulating patch proposals, and invoking sandbox execution.
              </p>
            </div>
          </div>
        ) : task.status === 'SUCCEEDED' ? (
          <div className="p-4 rounded-lg border border-emerald-500/30 bg-emerald-950/20 flex items-center gap-3">
            <CheckCircle2 className="h-5 w-5 text-emerald-400 shrink-0" />
            <div className="space-y-0.5">
              <span className="text-xs font-semibold text-emerald-200">
                Pipeline Completed Successfully
              </span>
              <p className="text-xs text-emerald-300/70">
                All workflow stages succeeded: agent analysis, sandbox verification, authorization, and GitHub remote push.
              </p>
            </div>
          </div>
        ) : null}

        {/* Workflow Component Details Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
          {/* Proposal Status */}
          <div className="p-3.5 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1.5">
            <div className="flex items-center gap-1.5 text-slate-400 text-[11px] font-medium">
              <FileCode2 className="h-3.5 w-3.5 text-blue-400" />
              <span>Proposed Change</span>
            </div>
            <div className="flex items-baseline justify-between">
              <span className="text-xs font-semibold text-slate-200">
                {approval ? `Version ${approval.proposedChangeVersion}` : 'Not Generated Yet'}
              </span>
              {approval && (
                <Badge variant="info" className="text-[10px]">
                  v{approval.proposedChangeVersion}
                </Badge>
              )}
            </div>
            <p className="text-[10px] text-slate-400">
              {approval
                ? `Associated with approval record ${approval.id.slice(0, 8)}...`
                : 'Generated upon agent code formulation completion.'}
            </p>
          </div>

          {/* Sandbox Execution Status */}
          <div className="p-3.5 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1.5">
            <div className="flex items-center gap-1.5 text-slate-400 text-[11px] font-medium">
              <Boxes className="h-3.5 w-3.5 text-purple-400" />
              <span>Sandbox Verification</span>
            </div>
            <div className="flex items-baseline justify-between">
              <span className="text-xs font-semibold text-slate-200">
                {approval?.sandboxExecutionStatus || 'Pending Sandbox'}
              </span>
              {approval && (
                <Badge
                  variant={
                    approval.sandboxExecutionStatus === 'SUCCEEDED'
                      ? 'success'
                      : approval.sandboxExecutionStatus === 'RUNNING'
                      ? 'info'
                      : approval.sandboxExecutionStatus === 'FAILED'
                      ? 'danger'
                      : 'neutral'
                  }
                  className="text-[10px]"
                >
                  {approval.sandboxExecutionStatus}
                </Badge>
              )}
            </div>
            <p className="text-[10px] text-slate-400">
              Isolated Docker sandbox compilation and regression suite verification.
            </p>
          </div>

          {/* GitHub Delivery Status */}
          <div className="p-3.5 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1.5">
            <div className="flex items-center justify-between text-slate-400 text-[11px] font-medium">
              <div className="flex items-center gap-1.5">
                <GitBranch className="h-3.5 w-3.5 text-emerald-400" />
                <span>GitHub Delivery</span>
              </div>
              {mutation && <GithubMutationStatusBadge status={mutation.status} />}
            </div>
            <div className="flex items-baseline justify-between">
              <div className="text-xs font-semibold text-slate-200">
                {mutation?.pullRequestNumber ? (
                  safePrUrl ? (
                    <a
                      href={safePrUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 font-semibold transition-colors"
                      title={`Open PR #${mutation.pullRequestNumber} on GitHub`}
                    >
                      PR #{mutation.pullRequestNumber}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  ) : (
                    <span>PR #{mutation.pullRequestNumber}</span>
                  )
                ) : mutation?.branchName ? (
                  <span className="font-mono text-[11px] text-slate-200 truncate">
                    {mutation.branchName}
                  </span>
                ) : hasPrCreated ? (
                  'Pull Request Created'
                ) : hasBranchCreated ? (
                  'Branch Pushed'
                ) : task.branchName ? (
                  'Branch Assigned'
                ) : approval?.status === 'APPROVED' ? (
                  'Approved (Pending Delivery)'
                ) : approval ? (
                  'Awaiting Approval'
                ) : (
                  'Awaiting Proposal'
                )}
              </div>
              {!mutation && hasPrCreated && (
                <Badge variant="success" className="text-[10px]">
                  PR Open
                </Badge>
              )}
            </div>
            <p className="text-[10px] text-slate-400">
              {mutation?.commitSha ? (
                <span className="font-mono">
                  Commit {mutation.commitSha.slice(0, 7)}
                  {mutation.branchName ? ` on ${mutation.branchName}` : ''}
                </span>
              ) : mutation?.branchName ? (
                `Target branch: ${mutation.branchName}`
              ) : task.branchName ? (
                `Target branch: ${task.branchName}`
              ) : (
                'Remote branch and PR created after human approval.'
              )}
            </p>
            {approval && (
              <div className="pt-1">
                <Link
                  to={`/organizations/${organizationId}/approvals/${approval.id}`}
                  className="text-[11px] text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 font-medium transition-colors"
                >
                  View Approval & Delivery
                  <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Task Details and Event Timeline Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Metadata Specifications */}
        <div className="lg:col-span-1 space-y-6">
          <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400 pb-2 border-b border-slate-800/60">
              Task Specifications
            </h2>

            <dl className="space-y-3 text-xs">
              <div>
                <dt className="text-slate-400 text-[11px]">Task Identifier</dt>
                <dd className="font-mono text-slate-200 text-[11px] mt-0.5 break-all">
                  {task.id}
                </dd>
              </div>

              <div>
                <dt className="text-slate-400 text-[11px]">Source Origin</dt>
                <dd className="mt-0.5 flex items-center gap-2">
                  <Badge variant="neutral" className="text-[10px] font-mono">
                    {task.sourceType}
                  </Badge>
                  {task.sourceReference && (
                    <span className="font-mono text-slate-300 text-[11px]">
                      {task.sourceReference}
                    </span>
                  )}
                </dd>
              </div>

              <div>
                <dt className="text-slate-400 text-[11px]">Current Step</dt>
                <dd className="font-mono text-slate-200 text-[11px] mt-0.5">
                  {task.currentStep || 'None (Worker idle)'}
                </dd>
              </div>

              {mutation?.branchName && (
                <div>
                  <dt className="text-slate-400 text-[11px]">Remote Branch</dt>
                  <dd className="font-mono text-slate-200 text-[11px] mt-0.5 truncate">
                    {mutation.branchName}
                  </dd>
                </div>
              )}

              {mutation?.pullRequestNumber && (
                <div>
                  <dt className="text-slate-400 text-[11px]">Pull Request</dt>
                  <dd className="mt-0.5">
                    {safePrUrl ? (
                      <a
                        href={safePrUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 font-mono text-[11px] font-semibold transition-colors"
                      >
                        PR #{mutation.pullRequestNumber}
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    ) : (
                      <span className="font-mono text-slate-200 text-[11px]">
                        PR #{mutation.pullRequestNumber}
                      </span>
                    )}
                  </dd>
                </div>
              )}

              {mutation?.commitSha && (
                <div>
                  <dt className="text-slate-400 text-[11px]">Commit SHA</dt>
                  <dd className="font-mono text-slate-200 text-[11px] mt-0.5">
                    {mutation.commitSha.slice(0, 7)}
                  </dd>
                </div>
              )}

              <div>
                <dt className="text-slate-400 text-[11px]">Created At</dt>
                <dd className="text-slate-200 font-mono text-[11px] mt-0.5">
                  {createdAtFormatted}
                </dd>
              </div>

              {startedAtFormatted && (
                <div>
                  <dt className="text-slate-400 text-[11px]">Started At</dt>
                  <dd className="text-slate-200 font-mono text-[11px] mt-0.5">
                    {startedAtFormatted}
                  </dd>
                </div>
              )}

              {finishedAtFormatted && (
                <div>
                  <dt className="text-slate-400 text-[11px]">Finished At</dt>
                  <dd className="text-slate-200 font-mono text-[11px] mt-0.5">
                    {finishedAtFormatted}
                  </dd>
                </div>
              )}

              {task.startedAt && task.finishedAt && (
                <div>
                  <dt className="text-slate-400 text-[11px]">Duration</dt>
                  <dd className="text-slate-200 font-mono text-[11px] mt-0.5">
                    {Math.max(
                      0,
                      Math.round(
                        (new Date(task.finishedAt).getTime() -
                          new Date(task.startedAt).getTime()) /
                          1000
                      )
                    )}
                    s
                  </dd>
                </div>
              )}
            </dl>
          </div>
        </div>

        {/* Right Column: Event Timeline */}
        <div className="lg:col-span-2">
          <TaskEventTimeline events={events} isLoading={isLoadingEvents} />
        </div>
      </div>

      {/* Cancellation Confirmation Dialog */}
      <Dialog
        isOpen={isCancelDialogOpen}
        onClose={() => {
          if (!cancelMutation.isPending) {
            setIsCancelDialogOpen(false)
          }
        }}
        title="Confirm Task Cancellation"
        description="Are you sure you want to cancel this active engineering task? This will abort any executing worker or agent pipeline."
      >
        <div className="space-y-4">
          {cancelError && (
            <ErrorMessage
              title="Cancellation Failed"
              message={cancelError.message}
              code={cancelError.code}
              requestId={cancelError.requestId}
            />
          )}

          <div className="p-3.5 rounded-lg border border-amber-500/30 bg-amber-950/20 text-xs text-amber-200 space-y-1">
            <p className="font-semibold text-amber-300">Irreversible Action</p>
            <p className="leading-relaxed">
              Once cancelled, the task state machine enters a terminal state (<code className="font-mono text-amber-100">CANCELLED</code>) and cannot be resumed.
            </p>
          </div>

          <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-800/80">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsCancelDialogOpen(false)}
              disabled={cancelMutation.isPending}
            >
              Keep Running
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => cancelMutation.mutate()}
              isLoading={cancelMutation.isPending}
              disabled={cancelMutation.isPending}
            >
              <Ban className="h-3.5 w-3.5 mr-1.5" />
              Confirm Cancellation
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  )
}
