import React from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface DashboardMetricCardProps {
  title: string
  value: number | string | null
  description: string
  icon: LucideIcon
  to?: string
  variant?: 'blue' | 'purple' | 'amber' | 'emerald' | 'slate'
  isLoading?: boolean
  className?: string
}

const variantStyles = {
  blue: {
    iconBg: 'bg-blue-600/15 border-blue-500/30 text-blue-400',
    accentBorder: 'hover:border-blue-500/50',
    badge: 'text-blue-400',
  },
  purple: {
    iconBg: 'bg-purple-600/15 border-purple-500/30 text-purple-400',
    accentBorder: 'hover:border-purple-500/50',
    badge: 'text-purple-400',
  },
  amber: {
    iconBg: 'bg-amber-600/15 border-amber-500/30 text-amber-400',
    accentBorder: 'hover:border-amber-500/50',
    badge: 'text-amber-400',
  },
  emerald: {
    iconBg: 'bg-emerald-600/15 border-emerald-500/30 text-emerald-400',
    accentBorder: 'hover:border-emerald-500/50',
    badge: 'text-emerald-400',
  },
  slate: {
    iconBg: 'bg-slate-700/20 border-slate-700/50 text-slate-400',
    accentBorder: 'hover:border-slate-600',
    badge: 'text-slate-400',
  },
}

export const DashboardMetricCard: React.FC<DashboardMetricCardProps> = ({
  title,
  value,
  description,
  icon: Icon,
  to,
  variant = 'blue',
  isLoading = false,
  className,
}) => {
  const styles = variantStyles[variant]

  const content = (
    <div
      className={cn(
        'rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs transition-all duration-150',
        to ? `cursor-pointer ${styles.accentBorder} hover:bg-slate-800/20` : '',
        className
      )}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <p className="text-xs font-medium text-slate-400">{title}</p>
          {isLoading ? (
            <div className="h-8 w-16 bg-slate-800 animate-pulse rounded my-1" />
          ) : (
            <p className="text-2xl font-bold tracking-tight text-white font-mono">
              {value !== null && value !== undefined ? value : '—'}
            </p>
          )}
        </div>
        <div
          className={cn(
            'h-10 w-10 rounded-lg border flex items-center justify-center shrink-0 transition-colors',
            styles.iconBg
          )}
          aria-hidden="true"
        >
          <Icon className="h-5 w-5" />
        </div>
      </div>

      <div className="mt-3 pt-3 border-t border-slate-800/60 flex items-center justify-between text-xs">
        <p className="text-[11px] text-slate-400 truncate max-w-[200px]" title={description}>
          {description}
        </p>
        {to && (
          <span className="inline-flex items-center gap-1 text-[11px] text-slate-400 group-hover:text-slate-200 transition-colors">
            <span>View</span>
            <ArrowRight className="h-3 w-3" />
          </span>
        )}
      </div>
    </div>
  )

  if (to) {
    return (
      <Link to={to} className="block group focus:outline-hidden focus-visible:ring-2 focus-visible:ring-blue-500 rounded-xl" aria-label={`${title}: ${value ?? 0}. ${description}`}>
        {content}
      </Link>
    )
  }

  return content
}
