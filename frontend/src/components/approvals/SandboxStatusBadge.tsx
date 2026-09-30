import React from 'react'
import {
  CheckCircle2,
  Play,
  Clock,
  XCircle,
  AlertTriangle,
  AlertCircle,
  Ban,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { SandboxExecutionStatus } from '@/types/api'

interface SandboxStatusBadgeProps {
  status: SandboxExecutionStatus
  className?: string
  showIcon?: boolean
}

export const SandboxStatusBadge: React.FC<SandboxStatusBadgeProps> = ({
  status,
  className,
  showIcon = true,
}) => {
  switch (status) {
    case 'SUCCEEDED':
      return (
        <Badge variant="success" className={className}>
          {showIcon && <CheckCircle2 className="h-3 w-3 mr-1 text-emerald-400" />}
          Sandbox Passed
        </Badge>
      )
    case 'RUNNING':
      return (
        <Badge variant="info" className={className}>
          {showIcon && <Play className="h-3 w-3 mr-1 text-blue-400 animate-pulse" />}
          Sandbox Running
        </Badge>
      )
    case 'QUEUED':
      return (
        <Badge variant="neutral" className={className}>
          {showIcon && <Clock className="h-3 w-3 mr-1 text-slate-400" />}
          Sandbox Queued
        </Badge>
      )
    case 'FAILED':
      return (
        <Badge variant="danger" className={className}>
          {showIcon && <XCircle className="h-3 w-3 mr-1 text-red-400" />}
          Sandbox Failed
        </Badge>
      )
    case 'TIMED_OUT':
      return (
        <Badge variant="warning" className={className}>
          {showIcon && <AlertTriangle className="h-3 w-3 mr-1 text-amber-400" />}
          Sandbox Timed Out
        </Badge>
      )
    case 'RESOURCE_LIMIT':
      return (
        <Badge variant="warning" className={className}>
          {showIcon && <AlertTriangle className="h-3 w-3 mr-1 text-amber-400" />}
          Resource Limit Exceeded
        </Badge>
      )
    case 'EXECUTOR_UNAVAILABLE':
      return (
        <Badge variant="danger" className={className}>
          {showIcon && <AlertCircle className="h-3 w-3 mr-1 text-red-400" />}
          Executor Unavailable
        </Badge>
      )
    case 'CANCELLED':
      return (
        <Badge variant="neutral" className={className}>
          {showIcon && <Ban className="h-3 w-3 mr-1 text-slate-400" />}
          Sandbox Cancelled
        </Badge>
      )
    default:
      return (
        <Badge variant="neutral" className={className}>
          {status}
        </Badge>
      )
  }
}
