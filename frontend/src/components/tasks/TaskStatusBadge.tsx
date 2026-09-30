import React from 'react'
import {
  Clock,
  Play,
  Hourglass,
  CheckCircle2,
  XCircle,
  Ban,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { TaskStatus } from '@/types/api'

interface TaskStatusBadgeProps {
  status: TaskStatus
  className?: string
  showIcon?: boolean
}

export const TaskStatusBadge: React.FC<TaskStatusBadgeProps> = ({
  status,
  className,
  showIcon = true,
}) => {
  switch (status) {
    case 'QUEUED':
      return (
        <Badge variant="neutral" className={className}>
          {showIcon && <Clock className="h-3 w-3 mr-1 text-slate-400" />}
          Queued
        </Badge>
      )
    case 'RUNNING':
      return (
        <Badge variant="info" className={className}>
          {showIcon && <Play className="h-3 w-3 mr-1 text-blue-400 animate-pulse" />}
          Running
        </Badge>
      )
    case 'WAITING_FOR_APPROVAL':
      return (
        <Badge variant="purple" className={className}>
          {showIcon && <Hourglass className="h-3 w-3 mr-1 text-purple-400" />}
          Approval Required
        </Badge>
      )
    case 'SUCCEEDED':
      return (
        <Badge variant="success" className={className}>
          {showIcon && <CheckCircle2 className="h-3 w-3 mr-1 text-emerald-400" />}
          Succeeded
        </Badge>
      )
    case 'FAILED':
      return (
        <Badge variant="danger" className={className}>
          {showIcon && <XCircle className="h-3 w-3 mr-1 text-red-400" />}
          Failed
        </Badge>
      )
    case 'CANCELLED':
      return (
        <Badge variant="neutral" className={className}>
          {showIcon && <Ban className="h-3 w-3 mr-1 text-slate-500" />}
          Cancelled
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
