import React from 'react'
import { Link } from 'react-router-dom'
import {
  Clock,
  Play,
  Hourglass,
  CheckCircle2,
  XCircle,
  Ban,
  GitBranch,
  GitCommit,
  GitPullRequest,
  AlertTriangle,
  FileCheck,
  CheckCheck,
  XOctagon,
  RefreshCw,
  ArrowRight,
  User,
  Cpu,
  Server,
  Activity,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { cn } from '@/lib/utils'
import type { TaskEventType, TaskActorSource } from '@/types/api'

export interface EnrichedActivityEvent {
  id: string
  taskId: string
  taskTitle: string
  eventType: TaskEventType
  occurredAt: string
  actorSource: TaskActorSource
  previousState?: string | null
  newState?: string | null
}

interface RecentActivityProps {
  events: EnrichedActivityEvent[]
  organizationId: string
  isLoading?: boolean
  className?: string
}

interface EventMeta {
  label: string
  icon: React.ComponentType<{ className?: string }>
  iconClass: string
}

function getEventMeta(type: TaskEventType): EventMeta {
  switch (type) {
    case 'TASK_CREATED':
      return {
        label: 'Task Created',
        icon: Clock,
        iconClass: 'text-slate-400 bg-slate-800/80 border-slate-700/60',
      }
    case 'TASK_STARTED':
      return {
        label: 'Agent Formulation Started',
        icon: Play,
        iconClass: 'text-blue-400 bg-blue-950/60 border-blue-500/40',
      }
    case 'TASK_APPROVAL_REQUIRED':
      return {
        label: 'Human Review Gate Required',
        icon: Hourglass,
        iconClass: 'text-purple-400 bg-purple-950/60 border-purple-500/40',
      }
    case 'TASK_RESUMED':
      return {
        label: 'Execution Resumed',
        icon: RefreshCw,
        iconClass: 'text-blue-400 bg-blue-950/60 border-blue-500/40',
      }
    case 'TASK_SUCCEEDED':
      return {
        label: 'Task Succeeded',
        icon: CheckCircle2,
        iconClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-500/40',
      }
    case 'TASK_FAILED':
      return {
        label: 'Task Failed',
        icon: XCircle,
        iconClass: 'text-red-400 bg-red-950/60 border-red-500/40',
      }
    case 'TASK_CANCELLED':
      return {
        label: 'Task Cancelled',
        icon: Ban,
        iconClass: 'text-slate-400 bg-slate-800 border-slate-700',
      }
    case 'APPROVAL_REQUESTED':
      return {
        label: 'Approval Requested',
        icon: FileCheck,
        iconClass: 'text-purple-400 bg-purple-950/60 border-purple-500/40',
      }
    case 'APPROVAL_APPROVED':
      return {
        label: 'Proposal Approved',
        icon: CheckCheck,
        iconClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-500/40',
      }
    case 'APPROVAL_REJECTED':
      return {
        label: 'Proposal Rejected',
        icon: XOctagon,
        iconClass: 'text-amber-400 bg-amber-950/60 border-amber-500/40',
      }
    case 'APPROVAL_CANCELLED':
      return {
        label: 'Approval Cancelled',
        icon: Ban,
        iconClass: 'text-slate-400 bg-slate-800 border-slate-700',
      }
    case 'GITHUB_BRANCH_CREATED':
      return {
        label: 'Remote Branch Created',
        icon: GitBranch,
        iconClass: 'text-sky-400 bg-sky-950/60 border-sky-500/40',
      }
    case 'GITHUB_COMMIT_CREATED':
      return {
        label: 'Patch Committed to Branch',
        icon: GitCommit,
        iconClass: 'text-sky-400 bg-sky-950/60 border-sky-500/40',
      }
    case 'GITHUB_PR_CREATED':
      return {
        label: 'Pull Request Opened',
        icon: GitPullRequest,
        iconClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-500/40',
      }
    case 'GITHUB_MUTATION_FAILED':
      return {
        label: 'GitHub Delivery Failed',
        icon: AlertTriangle,
        iconClass: 'text-red-400 bg-red-950/60 border-red-500/40',
      }
    default:
      return {
        label: type,
        icon: Activity,
        iconClass: 'text-slate-400 bg-slate-800 border-slate-700',
      }
  }
}

function renderActorBadge(actorSource: TaskActorSource) {
  switch (actorSource) {
    case 'USER':
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-slate-400 font-mono">
          <User className="h-3 w-3 text-slate-400" />
          <span>User</span>
        </span>
      )
    case 'WORKER':
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-purple-400 font-mono">
          <Cpu className="h-3 w-3 text-purple-400" />
          <span>Worker</span>
        </span>
      )
    case 'SYSTEM':
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-blue-400 font-mono">
          <Server className="h-3 w-3 text-blue-400" />
          <span>System</span>
        </span>
      )
    default:
      return null
  }
}

export const RecentActivity: React.FC<RecentActivityProps> = ({
  events,
  organizationId,
  isLoading = false,
  className,
}) => {
  if (isLoading) {
    return (
      <div className={cn('rounded-xl border border-slate-800/80 bg-[#0e131d] p-6 shadow-xs', className)}>
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
            Recent Activity
          </h2>
          <Badge variant="neutral" className="text-[10px]">Loading</Badge>
        </div>
        <div className="py-8 flex justify-center">
          <LoadingSpinner size="sm" label="Loading recent events..." />
        </div>
      </div>
    )
  }

  if (events.length === 0) {
    return (
      <div className={cn('rounded-xl border border-slate-800/80 bg-[#0e131d] p-6 shadow-xs', className)}>
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
            Recent Activity
          </h2>
          <span className="text-[10px] text-slate-400 font-mono">0 events</span>
        </div>
        <div className="py-10 text-center space-y-2">
          <Activity className="h-8 w-8 text-slate-600 mx-auto" />
          <p className="text-xs text-slate-300 font-medium">No recent activity</p>
          <p className="text-[11px] text-slate-400 max-w-sm mx-auto">
            Autonomous agent proposals, sandbox runs, and GitHub deliveries will be recorded here in real-time.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className={cn('rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4', className)}>
      <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
        <div className="flex items-center gap-2">
          <Activity className="h-4 w-4 text-blue-400" />
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-300 font-mono">
            Recent Engineering Activity
          </h2>
        </div>
        <span className="text-[10px] text-slate-400 font-mono">
          {events.length} event{events.length === 1 ? '' : 's'}
        </span>
      </div>

      <div className="divide-y divide-slate-800/50">
        {events.map((event) => {
          const meta = getEventMeta(event.eventType)
          const Icon = meta.icon
          const formattedDate = new Date(event.occurredAt).toLocaleString(undefined, {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
          })

          return (
            <Link
              key={event.id}
              to={`/organizations/${organizationId}/tasks/${event.taskId}`}
              className="group flex items-start gap-3.5 py-3 hover:bg-slate-800/20 px-2 -mx-2 rounded-lg transition-colors"
            >
              <div
                className={cn(
                  'h-8 w-8 rounded-lg border flex items-center justify-center shrink-0 mt-0.5',
                  meta.iconClass
                )}
                aria-hidden="true"
              >
                <Icon className="h-4 w-4" />
              </div>

              <div className="flex-1 min-w-0 space-y-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold text-slate-200 group-hover:text-blue-400 transition-colors">
                    {meta.label}
                  </span>
                  <span className="text-[10px] text-slate-400 font-mono shrink-0">
                    {formattedDate}
                  </span>
                </div>

                <p className="text-xs text-slate-400 truncate" title={event.taskTitle}>
                  {event.taskTitle}
                </p>

                <div className="flex items-center gap-3 pt-0.5 text-[10px] text-slate-400">
                  {renderActorBadge(event.actorSource)}
                  {event.newState && (
                    <span className="font-mono text-slate-400">
                      State: <span className="text-slate-300 font-semibold">{event.newState}</span>
                    </span>
                  )}
                </div>
              </div>

              <ArrowRight className="h-3.5 w-3.5 text-slate-600 group-hover:text-slate-400 group-hover:translate-x-0.5 transition-all shrink-0 mt-2" />
            </Link>
          )
        })}
      </div>
    </div>
  )
}
