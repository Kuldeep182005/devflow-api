import React from 'react'
import {
  Hourglass,
  CheckCircle2,
  XCircle,
  Ban,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { ApprovalStatus } from '@/types/api'

interface ApprovalStatusBadgeProps {
  status: ApprovalStatus
  className?: string
  showIcon?: boolean
}

export const ApprovalStatusBadge: React.FC<ApprovalStatusBadgeProps> = ({
  status,
  className,
  showIcon = true,
}) => {
  switch (status) {
    case 'PENDING':
      return (
        <Badge variant="purple" className={className}>
          {showIcon && <Hourglass className="h-3 w-3 mr-1 text-purple-400" />}
          Pending Review
        </Badge>
      )
    case 'APPROVED':
      return (
        <Badge variant="success" className={className}>
          {showIcon && <CheckCircle2 className="h-3 w-3 mr-1 text-emerald-400" />}
          Approved
        </Badge>
      )
    case 'REJECTED':
      return (
        <Badge variant="danger" className={className}>
          {showIcon && <XCircle className="h-3 w-3 mr-1 text-red-400" />}
          Rejected
        </Badge>
      )
    case 'CANCELLED':
      return (
        <Badge variant="neutral" className={className}>
          {showIcon && <Ban className="h-3 w-3 mr-1 text-slate-400" />}
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
