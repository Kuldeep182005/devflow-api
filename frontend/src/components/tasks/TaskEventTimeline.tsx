import React from 'react'
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
  User,
  Cpu,
  Server,
  ArrowRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { TaskStatusBadge } from '@/components/tasks/TaskStatusBadge'
import type { TaskEventResponse, TaskEventType, TaskActorSource } from '@/types/api'

interface TaskEventTimelineProps {
  events: TaskEventResponse[]
  isLoading?: boolean
  className?: string
}

interface EventMetadata {
  label: string
  description: string
  icon: React.ComponentType<{ className?: string }>
  color: string
}

function getEventMeta(type: TaskEventType): EventMetadata {
  switch (type) {
    case 'TASK_CREATED':
      return {
        label: 'Task Created',
        description: 'Task registered and placed in queue',
        icon: Clock,
        color: 'text-slate-400 bg-slate-800/80 border-slate-700/60',
      }
    case 'TASK_STARTED':
      return {
        label: 'Agent Started',
        description: 'Worker dispatched agent to analyze codebase and formulate patch',
        icon: Play,
        color: 'text-blue-400 bg-blue-950/60 border-blue-500/40',
      }
    case 'TASK_APPROVAL_REQUIRED':
      return {
        label: 'Approval Required',
        description: 'Proposed changes generated and sandbox verified; awaiting human review',
        icon: Hourglass,
        color: 'text-purple-400 bg-purple-950/60 border-purple-500/40',
      }
    case 'TASK_RESUMED':
      return {
        label: 'Task Resumed',
        description: 'Execution resumed following approval decision',
        icon: RefreshCw,
        color: 'text-blue-400 bg-blue-950/60 border-blue-500/40',
      }
    case 'TASK_SUCCEEDED':
      return {
        label: 'Task Succeeded',
        description: 'Full workflow completed successfully',
        icon: CheckCircle2,
        color: 'text-emerald-400 bg-emerald-950/60 border-emerald-500/40',
      }
    case 'TASK_FAILED':
      return {
        label: 'Task Failed',
        description: 'Agent or worker execution encountered a terminal error',
        icon: XCircle,
        color: 'text-red-400 bg-red-950/60 border-red-500/40',
      }
    case 'TASK_CANCELLED':
      return {
        label: 'Task Cancelled',
        description: 'Execution aborted by user request',
        icon: Ban,
        color: 'text-slate-400 bg-slate-800 border-slate-700',
      }
    case 'APPROVAL_REQUESTED':
      return {
        label: 'Approval Requested',
        description: 'Sandbox verification passed and human gate requested',
        icon: FileCheck,
        color: 'text-purple-400 bg-purple-950/60 border-purple-500/40',
      }
    case 'APPROVAL_APPROVED':
      return {
        label: 'Proposal Approved',
        description: 'Reviewer authorized code change for branch creation',
        icon: CheckCheck,
        color: 'text-emerald-400 bg-emerald-950/60 border-emerald-500/40',
      }
    case 'APPROVAL_REJECTED':
      return {
        label: 'Proposal Rejected',
        description: 'Reviewer declined the proposed patch',
        icon: XOctagon,
        color: 'text-amber-400 bg-amber-950/60 border-amber-500/40',
      }
    case 'APPROVAL_CANCELLED':
      return {
        label: 'Approval Cancelled',
        description: 'Pending approval gate superseded or cancelled',
        icon: Ban,
        color: 'text-slate-400 bg-slate-800 border-slate-700',
      }
    case 'GITHUB_BRANCH_CREATED':
      return {
        label: 'GitHub Branch Created',
        description: 'Remote branch pushed to target repository',
        icon: GitBranch,
        color: 'text-sky-400 bg-sky-950/60 border-sky-500/40',
      }
    case 'GITHUB_COMMIT_CREATED':
      return {
        label: 'GitHub Commit Created',
        description: 'Patch committed to remote branch via GitHub App',
        icon: GitCommit,
        color: 'text-sky-400 bg-sky-950/60 border-sky-500/40',
      }
    case 'GITHUB_PR_CREATED':
      return {
        label: 'GitHub PR Created',
        description: 'Pull Request opened on remote GitHub repository',
        icon: GitPullRequest,
        color: 'text-emerald-400 bg-emerald-950/60 border-emerald-500/40',
      }
    case 'GITHUB_MUTATION_FAILED':
      return {
        label: 'GitHub Mutation Failed',
        description: 'Failed to create branch, commit, or pull request on GitHub',
        icon: AlertTriangle,
        color: 'text-red-400 bg-red-950/60 border-red-500/40',
      }
    default:
      return {
        label: type,
        description: 'Task state transition recorded',
        icon: Clock,
        color: 'text-slate-400 bg-slate-800 border-slate-700',
      }
  }
}

function renderActorBadge(actorSource: TaskActorSource) {
  switch (actorSource) {
    case 'USER':
      return (
        <Badge variant="neutral" className="text-[10px] gap-1 px-1.5 py-0 border-slate-700">
          <User className="h-2.5 w-2.5 text-cyan-400" />
          <span className="text-cyan-300">USER</span>
        </Badge>
      )
    case 'WORKER':
      return (
        <Badge variant="purple" className="text-[10px] gap-1 px-1.5 py-0 border-purple-800/60">
          <Cpu className="h-2.5 w-2.5 text-purple-400" />
          <span className="text-purple-300">WORKER</span>
        </Badge>
      )
    case 'SYSTEM':
      return (
        <Badge variant="neutral" className="text-[10px] gap-1 px-1.5 py-0 border-slate-700">
          <Server className="h-2.5 w-2.5 text-slate-400" />
          <span className="text-slate-400">SYSTEM</span>
        </Badge>
      )
    default:
      return (
        <Badge variant="neutral" className="text-[10px] px-1.5 py-0">
          {actorSource}
        </Badge>
      )
  }
}

export const TaskEventTimeline: React.FC<TaskEventTimelineProps> = ({
  events,
  isLoading = false,
  className,
}) => {
  if (isLoading) {
    return (
      <div className={cn('p-6 text-center text-xs text-slate-400 space-y-3', className)}>
        <div className="h-4 w-32 bg-slate-800/80 rounded-md animate-pulse mx-auto" />
        <div className="space-y-2">
          <div className="h-12 bg-slate-900/60 rounded-lg animate-pulse" />
          <div className="h-12 bg-slate-900/60 rounded-lg animate-pulse" />
        </div>
      </div>
    )
  }

  if (!events || events.length === 0) {
    return (
      <div
        className={cn(
          'p-8 text-center rounded-xl border border-slate-800/60 bg-[#0f141f] text-slate-400',
          className
        )}
      >
        <Clock className="h-8 w-8 mx-auto text-slate-400 mb-2" />
        <p className="text-xs font-medium text-slate-400">No events recorded yet</p>
        <p className="text-[11px] text-slate-400 mt-1">
          State changes and worker activities will appear here chronologically as the task progresses.
        </p>
      </div>
    )
  }

  // Sort events chronologically (oldest first or newest first; backend returns ordered, but ensure deterministic sort)
  const sortedEvents = [...events].sort(
    (a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime()
  )

  return (
    <div
      className={cn('relative rounded-xl border border-slate-800/80 bg-[#0f141f] p-5', className)}
      aria-label="Event Timeline"
    >
      <div className="mb-4 pb-3 border-b border-slate-800/60 flex items-center justify-between">
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Audit Event Timeline
          </h3>
          <p className="text-[11px] text-slate-400 mt-0.5">
            Immutable log of state transitions and asynchronous actor events
          </p>
        </div>
        <span className="text-[11px] font-mono text-slate-400 bg-slate-900 px-2 py-0.5 rounded-md border border-slate-800">
          {events.length} {events.length === 1 ? 'event' : 'events'}
        </span>
      </div>

      <div className="relative pl-6 space-y-6 before:absolute before:left-3.5 before:top-2 before:bottom-2 before:w-[2px] before:bg-slate-800/90">
        {sortedEvents.map((event) => {
          const meta = getEventMeta(event.eventType)
          const Icon = meta.icon
          const time = new Date(event.occurredAt).toLocaleString(undefined, {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
          })

          return (
            <div key={event.id} className="relative group">
              {/* Timeline dot / icon */}
              <div
                className={cn(
                  'absolute -left-6 top-1 h-7 w-7 rounded-full border flex items-center justify-center -translate-x-1/2 transition-transform duration-200 group-hover:scale-110 shadow-xs',
                  meta.color
                )}
                title={meta.label}
              >
                <Icon className="h-3.5 w-3.5" />
              </div>

              {/* Event card content */}
              <div className="rounded-lg border border-slate-800/60 bg-slate-900/40 hover:bg-slate-900/70 p-3.5 transition-colors">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-white tracking-tight">
                      {meta.label}
                    </span>
                    {renderActorBadge(event.actorSource)}
                  </div>
                  <time className="text-[11px] text-slate-400 font-mono" dateTime={event.occurredAt}>
                    {time}
                  </time>
                </div>

                <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                  {meta.description}
                </p>

                {/* State Transition details if previousState -> newState changed */}
                <div className="mt-2.5 pt-2 border-t border-slate-800/60 flex flex-wrap items-center justify-between gap-2 text-[11px]">
                  <div className="flex items-center gap-1.5">
                    {event.previousState && (
                      <>
                        <TaskStatusBadge
                          status={event.previousState}
                          showIcon={false}
                          className="text-[10px] px-1.5 py-0"
                        />
                        <ArrowRight className="h-3 w-3 text-slate-400" />
                      </>
                    )}
                    <TaskStatusBadge
                      status={event.newState}
                      showIcon={false}
                      className="text-[10px] px-1.5 py-0 font-medium"
                    />
                  </div>

                  {event.correlationId && (
                    <span
                      className="text-[10px] font-mono text-slate-400 truncate max-w-[200px]"
                      title={`Correlation ID: ${event.correlationId}`}
                    >
                      id: {event.correlationId.slice(0, 8)}...
                    </span>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
