import React from 'react'
import {
  Clock,
  Play,
  GitBranch,
  GitCommit,
  GitPullRequest,
  CheckCircle2,
  XCircle,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { GithubMutationStatus } from '@/types/api'

interface GithubMutationStatusBadgeProps {
  status: GithubMutationStatus
  className?: string
  showIcon?: boolean
}

export const GithubMutationStatusBadge: React.FC<GithubMutationStatusBadgeProps> = ({
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
          Executing
        </Badge>
      )
    case 'BRANCH_CREATED':
      return (
        <Badge variant="info" className={className}>
          {showIcon && <GitBranch className="h-3 w-3 mr-1 text-sky-400" />}
          Branch Created
        </Badge>
      )
    case 'COMMITTED':
      return (
        <Badge variant="info" className={className}>
          {showIcon && <GitCommit className="h-3 w-3 mr-1 text-sky-400" />}
          Committed
        </Badge>
      )
    case 'PR_CREATED':
      return (
        <Badge variant="purple" className={className}>
          {showIcon && <GitPullRequest className="h-3 w-3 mr-1 text-purple-400" />}
          PR Created
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
    default:
      return (
        <Badge variant="neutral" className={className}>
          {status}
        </Badge>
      )
  }
}
