import React, { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  FolderGit2,
  Clock,
  ChevronLeft,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Boxes,
  FileCode2,
  User,
  CheckSquare2,
  ArrowRight,
  GitPullRequest,
  ShieldAlert,
  RotateCw,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { ApprovalStatusBadge } from '@/components/approvals/ApprovalStatusBadge'
import { SandboxStatusBadge } from '@/components/approvals/SandboxStatusBadge'
import { ApproveDialog } from '@/components/approvals/ApproveDialog'
import { RejectDialog } from '@/components/approvals/RejectDialog'
import { GithubMutationCard } from '@/components/github/GithubMutationCard'
import { ExecuteMutationDialog } from '@/components/github/ExecuteMutationDialog'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { useAuth } from '@/hooks/useAuth'
import { approvalsApi } from '@/api/approvals'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'

export const ApprovalDetailPage: React.FC = () => {
  const params = useParams<{ organizationId: string; approvalId: string }>()
  const navigate = useNavigate()
  const { session } = useAuth()
  const { organizations, activeOrganization } = useActiveOrganization()

  const organizationId = params.organizationId || activeOrganization?.id || ''
  const approvalId = params.approvalId || ''

  const currentOrg = organizations.find((o) => o.id === organizationId) || activeOrganization

  // Modal dialog states
  const [isApproveOpen, setIsApproveOpen] = useState(false)
  const [isRejectOpen, setIsRejectOpen] = useState(false)
  const [isExecuteOpen, setIsExecuteOpen] = useState(false)

  // Query Approval Record
  const {
    data: approval,
    isLoading: isLoadingApproval,
    isError: isApprovalError,
    error: approvalError,
    refetch: refetchApproval,
  } = useQuery({
    queryKey: ['organizations', organizationId, 'approvals', approvalId],
    queryFn: () => approvalsApi.get(organizationId, approvalId),
    enabled: !!organizationId && !!approvalId,
    // Poll every 4 seconds if pending to track concurrent review updates
    refetchInterval: (query) => {
      const data = query.state.data
      return data?.status === 'PENDING' ? 4000 : false
    },
  })

  // Query associated Task
  const { data: task } = useQuery({
    queryKey: ['organizations', organizationId, 'tasks', approval?.taskId],
    queryFn: () => tasksApi.get(organizationId, approval!.taskId),
    enabled: !!organizationId && !!approval?.taskId,
  })

  // Query associated Repository
  const { data: repository } = useQuery({
    queryKey: ['organizations', organizationId, 'repositories', approval?.repositoryId],
    queryFn: () => repositoriesApi.get(organizationId, approval!.repositoryId),
    enabled: !!organizationId && !!approval?.repositoryId,
  })

  // Query organization members to verify decider role
  const { data: membersPage } = useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: () => organizationsApi.listMembers(organizationId),
    enabled: !!organizationId,
  })

  // Query consequential GitHub mutation if status is APPROVED with bounded polling
  const {
    data: mutation,
    isLoading: isLoadingMutation,
    refetch: refetchMutation,
  } = useQuery({
    queryKey: ['organizations', organizationId, 'approvals', approvalId, 'mutation'],
    queryFn: () => approvalsApi.getMutation(organizationId, approvalId),
    enabled: !!organizationId && !!approvalId && approval?.status === 'APPROVED',
    retry: false,
    refetchInterval: (query) => {
      const data = query.state.data
      // Poll every 3 seconds only while in active non-terminal state
      if (data && data.status !== 'SUCCEEDED' && data.status !== 'FAILED') {
        return 3000
      }
      return false
    },
  })

  const currentMember = membersPage?.content?.find((m) => m.userId === session?.userId)
  const currentUserRole = currentMember?.role
  // Only OWNER and ADMIN can decide approvals in backend
  const canDecideApproval = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN'

  if (isLoadingApproval) {
    return (
      <div className="p-16 flex flex-col items-center justify-center space-y-3">
        <LoadingSpinner size="lg" />
        <p className="text-xs text-slate-400">Loading approval record from backend...</p>
      </div>
    )
  }

  if (isApprovalError || !approval) {
    const errorObj = approvalError instanceof ApiClientError ? approvalError : null
    return (
      <div className="p-8 max-w-5xl mx-auto w-full space-y-6">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate(`/organizations/${organizationId}/approvals`)}
        >
          <ChevronLeft className="h-4 w-4 mr-1" />
          Back to Approvals
        </Button>
        <ErrorMessage
          title="Approval Request Not Found"
          message={
            errorObj?.status === 404
              ? 'The requested approval record does not exist or you do not have permission to view it.'
              : errorObj?.message || 'Failed to load approval request.'
          }
          code={errorObj?.code}
          requestId={errorObj?.requestId}
          onRetry={() => refetchApproval()}
        />
      </div>
    )
  }

  const isPending = approval.status === 'PENDING'
  const isSandboxPassed = approval.sandboxExecutionStatus === 'SUCCEEDED'

  const requestedAtFormatted = new Date(approval.requestedAt).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  const decidedAtFormatted = approval.decidedAt
    ? new Date(approval.decidedAt).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
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
          to={`/organizations/${organizationId}/approvals`}
          className="hover:text-slate-200 font-medium text-slate-300"
        >
          Approvals
        </Link>
        <span>/</span>
        <span className="text-slate-100 font-mono truncate max-w-[200px]">
          {approval.id.slice(0, 8)}...
        </span>
      </div>

      {/* Main Header */}
      <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-6 shadow-xs">
        <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-xl font-bold text-white tracking-tight">
                {task ? task.title : `Approval for Task ${approval.taskId.slice(0, 8)}`}
              </h1>
              <ApprovalStatusBadge status={approval.status} />
              <SandboxStatusBadge status={approval.sandboxExecutionStatus} />
            </div>

            <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400 pt-1">
              <div className="flex items-center gap-1.5">
                <FolderGit2 className="h-3.5 w-3.5 text-slate-400" />
                {repository ? (
                  <Link
                    to={`/organizations/${organizationId}/repositories/${repository.id}`}
                    className="hover:text-purple-400 text-slate-300 font-mono"
                  >
                    {repository.fullName}
                  </Link>
                ) : (
                  <span className="font-mono text-slate-400">repo:{approval.repositoryId.slice(0, 8)}</span>
                )}
              </div>

              <div className="flex items-center gap-1.5">
                <CheckSquare2 className="h-3.5 w-3.5 text-slate-400" />
                <Link
                  to={`/organizations/${organizationId}/tasks/${approval.taskId}`}
                  className="hover:text-blue-400 text-slate-300 font-mono"
                >
                  task:{approval.taskId.slice(0, 8)}
                </Link>
              </div>

              <div className="flex items-center gap-1.5 font-mono">
                <Clock className="h-3.5 w-3.5 text-slate-400" />
                <span>Requested {requestedAtFormatted}</span>
              </div>
            </div>
          </div>

          {/* Decision Action Buttons (Visible only when PENDING) */}
          {isPending && (
            <div className="flex items-center gap-3 shrink-0 pt-2 lg:pt-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsRejectOpen(true)}
                disabled={!canDecideApproval}
                title={!canDecideApproval ? 'Only Owners and Admins can reject changes' : undefined}
                className="border-red-500/30 text-red-400 hover:bg-red-950/30 hover:text-red-300"
              >
                <XCircle className="h-3.5 w-3.5 mr-1.5" />
                Reject Proposal
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={() => setIsApproveOpen(true)}
                disabled={!canDecideApproval || !isSandboxPassed}
                title={
                  !canDecideApproval
                    ? 'Only Owners and Admins can approve changes'
                    : !isSandboxPassed
                    ? 'Cannot approve: sandbox execution must succeed first'
                    : 'Authorize this code change'
                }
                className="bg-emerald-600 hover:bg-emerald-500 text-white"
              >
                <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" />
                Approve Change
              </Button>
            </div>
          )}

          {/* Approved state: Execute / Retry GitHub Mutation action */}
          {approval.status === 'APPROVED' && (!mutation || mutation.status === 'FAILED') && (
            <div className="flex items-center gap-3 shrink-0 pt-2 lg:pt-0">
              {mutation?.status === 'FAILED' ? (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setIsExecuteOpen(true)}
                  disabled={!canDecideApproval}
                  title={!canDecideApproval ? 'Only Owners and Admins can retry execution' : undefined}
                  className="border-red-500/40 text-red-400 hover:bg-red-950/40 hover:text-red-300"
                >
                  <RotateCw className="h-3.5 w-3.5 mr-1.5" />
                  Retry GitHub Mutation
                </Button>
              ) : (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => setIsExecuteOpen(true)}
                  disabled={!canDecideApproval}
                  title={!canDecideApproval ? 'Only Owners and Admins can execute mutations' : undefined}
                  className="bg-blue-600 hover:bg-blue-500 text-white shadow-xs"
                >
                  <GitPullRequest className="h-3.5 w-3.5 mr-1.5" />
                  Execute on GitHub
                </Button>
              )}
            </div>
          )}
        </div>

        {/* RBAC UX Notice for non-deciders */}
        {isPending && !canDecideApproval && (
          <div className="mt-4 p-3 rounded-lg border border-slate-700/60 bg-slate-900/60 text-xs text-slate-300 flex items-center gap-2">
            <ShieldAlert className="h-4 w-4 text-amber-400 shrink-0" />
            <span>
              Your current role (<strong className="text-white font-mono">{currentUserRole || 'VIEWER'}</strong>) is read-only for change authorization. Only organization Owners and Admins can approve or reject proposals.
            </span>
          </div>
        )}
      </div>

      {/* Security Boundary Gateway Visualization */}
      <div className="rounded-xl border border-slate-800/80 bg-[#0f141f] p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between pb-2 border-b border-slate-800/60">
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              DevFlow Security Gate
            </h3>
            <p className="text-[11px] text-slate-400 mt-0.5">
              Consequential mutation architecture enforcing strict human authorization boundaries
            </p>
          </div>
          <Badge variant="purple" className="text-[10px]">
            Security Invariant
          </Badge>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-xs pt-1">
          <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40">
            <span className="text-[10px] text-slate-400 font-mono">Stage 1</span>
            <div className="font-semibold text-slate-200 mt-0.5">AI Formulation</div>
            <p className="text-[11px] text-slate-400 mt-1">Autonomous worker generates patch proposal</p>
          </div>

          <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40">
            <span className="text-[10px] text-slate-400 font-mono">Stage 2</span>
            <div className="font-semibold text-slate-200 mt-0.5">Sandbox Verification</div>
            <p className="text-[11px] text-slate-400 mt-1">Isolated compilation and test regression suite</p>
          </div>

          <div
            className={`p-3 rounded-lg border transition-all ${
              isPending
                ? 'border-purple-500/40 bg-purple-950/20 ring-1 ring-purple-500/30'
                : 'border-slate-800/60 bg-slate-900/40'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-purple-400 font-mono font-medium">Stage 3 (Active Gate)</span>
              {isPending && <span className="h-1.5 w-1.5 rounded-full bg-purple-400 animate-ping" />}
            </div>
            <div className="font-semibold text-purple-200 mt-0.5">Human Authorization</div>
            <p className="text-[11px] text-slate-400 mt-1">Explicit review required; zero automatic approval</p>
          </div>

          <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40">
            <span className="text-[10px] text-slate-400 font-mono">Stage 4</span>
            <div className="font-semibold text-slate-200 mt-0.5">GitHub Remote Delivery</div>
            <p className="text-[11px] text-slate-400 mt-1">Remote branch creation, commit push, and PR opening</p>
          </div>
        </div>
      </div>

      {/* Main Grid: Proposal Specifications & Sandbox Verification */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: Proposed Change Metadata Card */}
        <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
            <div className="flex items-center gap-2">
              <FileCode2 className="h-4 w-4 text-blue-400" />
              <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Proposed Change Specification
              </h2>
            </div>
            <Badge variant="info" className="text-[10px] font-mono">
              Version {approval.proposedChangeVersion}
            </Badge>
          </div>

          <dl className="space-y-3 text-xs">
            <div>
              <dt className="text-slate-400 text-[11px]">Proposal Identifier</dt>
              <dd className="font-mono text-slate-200 text-[11px] mt-0.5 break-all">
                {approval.proposedChangeId}
              </dd>
            </div>

            <div>
              <dt className="text-slate-400 text-[11px]">Target Repository</dt>
              <dd className="text-slate-200 text-[11px] mt-0.5 flex items-center gap-1.5">
                <FolderGit2 className="h-3.5 w-3.5 text-slate-400" />
                <span className="font-mono">{repository?.fullName || approval.repositoryId}</span>
              </dd>
            </div>

            <div>
              <dt className="text-slate-400 text-[11px]">Originating Task</dt>
              <dd className="text-slate-200 text-[11px] mt-0.5">
                <Link
                  to={`/organizations/${organizationId}/tasks/${approval.taskId}`}
                  className="text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 font-medium"
                >
                  {task?.title || `Task ${approval.taskId.slice(0, 8)}`}
                  <ArrowRight className="h-3 w-3" />
                </Link>
              </dd>
            </div>

            <div>
              <dt className="text-slate-400 text-[11px]">Requested By User</dt>
              <dd className="text-slate-300 text-[11px] mt-0.5 flex items-center gap-1.5 font-mono">
                <User className="h-3.5 w-3.5 text-slate-400" />
                <span>{approval.requestedByUserId}</span>
              </dd>
            </div>
          </dl>

          {/* Genuine API Capability Notification: No Fake Diffs */}
          <div className="mt-4 p-3.5 rounded-lg border border-slate-800 bg-slate-900/60 text-xs text-slate-400 space-y-1">
            <span className="font-semibold text-slate-300">File Diff Availability</span>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Detailed line-level diffs are not exposed directly through the public approval API contract. Proposal version, tenant ownership, and automated sandbox validation proofs are verified directly by the backend security boundary.
            </p>
          </div>
        </div>

        {/* Right: Sandbox Verification Status Card */}
        <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
            <div className="flex items-center gap-2">
              <Boxes className="h-4 w-4 text-purple-400" />
              <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Sandbox Execution Telemetry
              </h2>
            </div>
            <SandboxStatusBadge status={approval.sandboxExecutionStatus} />
          </div>

          <dl className="space-y-3 text-xs">
            <div>
              <dt className="text-slate-400 text-[11px]">Sandbox Execution Identifier</dt>
              <dd className="font-mono text-slate-200 text-[11px] mt-0.5 break-all">
                {approval.sandboxExecutionId}
              </dd>
            </div>

            <div>
              <dt className="text-slate-400 text-[11px]">Verification Result</dt>
              <dd className="text-slate-200 text-[11px] mt-0.5 font-mono">
                {approval.sandboxExecutionStatus}
              </dd>
            </div>
          </dl>

          {/* Sandbox Meaning Callout */}
          {isSandboxPassed ? (
            <div className="p-3.5 rounded-lg border border-emerald-500/30 bg-emerald-950/20 text-emerald-300 text-xs space-y-1">
              <div className="flex items-center gap-1.5 font-semibold text-emerald-200">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Sandbox Verification Passed</span>
              </div>
              <p className="text-[11px] text-emerald-200/80 leading-relaxed">
                The patch was compiled and tested within an ephemeral Docker sandbox container with zero host escapes. Automated verification passed, satisfying the prerequisite for human approval.
              </p>
            </div>
          ) : (
            <div className="p-3.5 rounded-lg border border-red-500/30 bg-red-950/20 text-red-300 text-xs space-y-1">
              <div className="flex items-center gap-1.5 font-semibold text-red-200">
                <AlertTriangle className="h-4 w-4 text-red-400 shrink-0" />
                <span>Sandbox Verification Incomplete or Failed</span>
              </div>
              <p className="text-[11px] text-red-200/80 leading-relaxed">
                Approval cannot proceed because automated sandbox verification did not succeed ({approval.sandboxExecutionStatus}). The backend state machine strictly enforces sandbox success prior to authorization.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Decision Audit Trail Card (if decided) */}
      {!isPending && (
        <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
              Recorded Review Decision
            </h2>
            <ApprovalStatusBadge status={approval.status} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div>
              <span className="text-slate-400 text-[11px]">Decided By (User)</span>
              <p className="font-mono text-slate-200 text-[11px] mt-0.5 break-all">
                {approval.decidedByUserId || 'System'}
              </p>
            </div>

            <div>
              <span className="text-slate-400 text-[11px]">Decided At</span>
              <p className="font-mono text-slate-200 text-[11px] mt-0.5">
                {decidedAtFormatted || 'N/A'}
              </p>
            </div>

            <div className="md:col-span-3">
              <span className="text-slate-400 text-[11px]">
                {approval.status === 'REJECTED' ? 'Rejection Reason' : 'Approval Review Note'}
              </span>
              <div className="mt-1 p-3 rounded-lg border border-slate-800 bg-slate-900/60 text-slate-200 font-mono text-xs whitespace-pre-wrap leading-relaxed">
                {approval.decisionReason || '(No additional note recorded)'}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Consequential GitHub Remote Mutation Section (for APPROVED approvals) */}
      {approval.status === 'APPROVED' && (
        <div className="space-y-4">
          {isLoadingMutation ? (
            <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-6 shadow-xs flex items-center justify-center gap-3 text-xs text-slate-400">
              <LoadingSpinner size="sm" />
              <span>Checking consequential GitHub mutation status...</span>
            </div>
          ) : mutation ? (
            <GithubMutationCard
              mutation={mutation}
              repositoryFullName={repository?.fullName}
              onRetry={() => setIsExecuteOpen(true)}
              canRetry={canDecideApproval}
            />
          ) : (
            <div className="rounded-xl border border-blue-500/30 bg-[#0e131d] p-5 shadow-xs space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-2 border-b border-slate-800/60">
                <div className="flex items-center gap-2">
                  <GitPullRequest className="h-4 w-4 text-blue-400" />
                  <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-200">
                    Ready for GitHub Remote Delivery
                  </h2>
                </div>
                <Badge variant="purple" className="text-[10px]">
                  Approved & Awaiting Execution
                </Badge>
              </div>

              <p className="text-xs text-slate-300/90 leading-relaxed">
                This proposed change has been authorized by human review. The downstream workflow is ready to create the remote branch, commit the approved patch, and open a pull request on GitHub.
              </p>

              <div className="pt-2 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                {canDecideApproval ? (
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setIsExecuteOpen(true)}
                    className="bg-blue-600 hover:bg-blue-500 text-white"
                  >
                    <GitPullRequest className="h-3.5 w-3.5 mr-1.5" />
                    Execute GitHub Mutation
                  </Button>
                ) : (
                  <div className="text-[11px] text-amber-300/90 bg-amber-950/20 border border-amber-500/30 px-3 py-2 rounded-lg flex items-center gap-2">
                    <ShieldAlert className="h-4 w-4 shrink-0 text-amber-400" />
                    <span>
                      Your role ({currentUserRole || 'VIEWER'}) is read-only. An organization Owner or Admin can trigger GitHub execution.
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Confirmation Dialogs */}
      <ApproveDialog
        isOpen={isApproveOpen}
        onClose={() => setIsApproveOpen(false)}
        organizationId={organizationId}
        approvalId={approvalId}
        taskId={approval.taskId}
      />

      <RejectDialog
        isOpen={isRejectOpen}
        onClose={() => setIsRejectOpen(false)}
        organizationId={organizationId}
        approvalId={approvalId}
        taskId={approval.taskId}
      />

      <ExecuteMutationDialog
        isOpen={isExecuteOpen}
        onClose={() => setIsExecuteOpen(false)}
        organizationId={organizationId}
        approvalId={approvalId}
        taskId={approval.taskId}
        repositoryFullName={repository?.fullName}
        branchName={task?.branchName || undefined}
        onSuccess={() => refetchMutation()}
      />
    </div>
  )
}
