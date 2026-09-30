import React from 'react'
import {
  Clock,
  Play,
  Hourglass,
  CheckCircle2,
  XCircle,
  Ban,
  GitPullRequest,
  Check,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import type { TaskStatus } from '@/types/api'

interface TaskLifecycleProps {
  status: TaskStatus
  className?: string
}

interface Step {
  id: string
  label: string
  description: string
  icon: React.ComponentType<{ className?: string }>
}

const HAPPY_PATH_STEPS: Step[] = [
  {
    id: 'QUEUED',
    label: 'Queued',
    description: 'Task awaiting worker pickup',
    icon: Clock,
  },
  {
    id: 'RUNNING',
    label: 'AI Agent Running',
    description: 'Analyzing repository & proposing patch',
    icon: Play,
  },
  {
    id: 'WAITING_FOR_APPROVAL',
    label: 'Approval Required',
    description: 'Sandbox passed, awaiting human review',
    icon: Hourglass,
  },
  {
    id: 'SUCCEEDED',
    label: 'Completed / Merged',
    description: 'Approved & committed to GitHub',
    icon: GitPullRequest,
  },
]

export const TaskLifecycle: React.FC<TaskLifecycleProps> = ({ status, className }) => {
  // Determine current step index in the happy path
  const getStepIndex = (st: TaskStatus): number => {
    switch (st) {
      case 'QUEUED':
        return 0
      case 'RUNNING':
        return 1
      case 'WAITING_FOR_APPROVAL':
        return 2
      case 'SUCCEEDED':
        return 3
      case 'FAILED':
      case 'CANCELLED':
        // For terminal failure/cancellation, we know it stopped execution
        return -1
      default:
        return 0
    }
  }

  const currentIndex = getStepIndex(status)
  const isFailed = status === 'FAILED'
  const isCancelled = status === 'CANCELLED'
  const isAllComplete = status === 'SUCCEEDED'

  return (
    <div
      className={cn(
        'rounded-xl border border-slate-800/80 bg-[#0f141f] p-5 shadow-xs',
        className
      )}
      aria-label="Task Lifecycle"
    >
      <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800/60">
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Task Execution Lifecycle
          </h3>
          <p className="text-[11px] text-slate-400 mt-0.5">
            Real-time pipeline progression tracked through the backend state machine
          </p>
        </div>

        {/* Terminal alternative alert badges */}
        {isFailed && (
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-red-950/60 border border-red-500/30 text-red-400 text-xs font-medium">
            <XCircle className="h-3.5 w-3.5" />
            <span>Execution Terminated (Failed)</span>
          </div>
        )}
        {isCancelled && (
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-900 border border-slate-700/60 text-slate-400 text-xs font-medium">
            <Ban className="h-3.5 w-3.5" />
            <span>Execution Cancelled</span>
          </div>
        )}
      </div>

      {/* Steps Visualizer */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 relative">
        {HAPPY_PATH_STEPS.map((step, idx) => {
          const StepIcon = step.icon
          const isPassed = !isFailed && !isCancelled && (idx < currentIndex || isAllComplete)
          const isCurrent = !isFailed && !isCancelled && !isAllComplete && idx === currentIndex

          let cardBorder = 'border-slate-800/60 bg-slate-900/40'
          let iconBg = 'bg-slate-800/80 text-slate-400'
          let textColor = 'text-slate-400'

          if (isPassed) {
            cardBorder = 'border-emerald-500/30 bg-emerald-950/10'
            iconBg = 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
            textColor = 'text-emerald-300'
          } else if (isCurrent) {
            if (step.id === 'WAITING_FOR_APPROVAL') {
              cardBorder = 'border-purple-500/40 bg-purple-950/20 ring-1 ring-purple-500/30'
              iconBg = 'bg-purple-500/20 text-purple-300 border border-purple-500/50 animate-pulse'
              textColor = 'text-purple-200'
            } else {
              cardBorder = 'border-blue-500/40 bg-blue-950/20 ring-1 ring-blue-500/30'
              iconBg = 'bg-blue-500/20 text-blue-300 border border-blue-500/50 animate-pulse'
              textColor = 'text-blue-200'
            }
          } else if (isFailed || isCancelled) {
            cardBorder = 'border-slate-800/40 bg-slate-900/20 opacity-60'
            iconBg = 'bg-slate-800 text-slate-400'
            textColor = 'text-slate-400'
          }

          return (
            <div
              key={step.id}
              className={cn(
                'relative flex flex-col p-3.5 rounded-lg border transition-all duration-200',
                cardBorder
              )}
            >
              <div className="flex items-center justify-between mb-2">
                <div
                  className={cn(
                    'h-7 w-7 rounded-md flex items-center justify-center transition-colors',
                    iconBg
                  )}
                >
                  {isPassed ? (
                    <Check className="h-3.5 w-3.5 text-emerald-400 stroke-[3]" />
                  ) : (
                    <StepIcon className="h-3.5 w-3.5" />
                  )}
                </div>
                <span className="text-[10px] font-mono text-slate-400">0{idx + 1}</span>
              </div>

              <span className={cn('text-xs font-semibold tracking-tight', textColor)}>
                {step.label}
              </span>
              <span className="text-[11px] text-slate-400 mt-1 leading-snug">
                {step.description}
              </span>

              {isCurrent && (
                <div className="mt-2.5 pt-2 border-t border-slate-700/40 flex items-center gap-1.5">
                  <span className="h-1.5 w-1.5 rounded-full bg-blue-400 animate-ping" />
                  <span className="text-[10px] font-medium text-blue-300">Active State</span>
                </div>
              )}
              {isPassed && (
                <div className="mt-2.5 pt-2 border-t border-emerald-900/40 flex items-center gap-1.5">
                  <CheckCircle2 className="h-3 w-3 text-emerald-400" />
                  <span className="text-[10px] font-medium text-emerald-400">Complete</span>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
