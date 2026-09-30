import React from 'react'
import { cn } from '@/lib/utils'

export interface LoadingSpinnerProps {
  className?: string
  size?: 'sm' | 'md' | 'lg'
  label?: string
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  className,
  size = 'md',
  label = 'Loading...',
}) => {
  const sizes = {
    sm: 'h-4 w-4 border-2',
    md: 'h-6 w-6 border-2',
    lg: 'h-10 w-10 border-3',
  }

  return (
    <div className={cn('flex flex-col items-center justify-center gap-3 p-6 text-slate-400', className)}>
      <div
        className={cn(
          'animate-spin rounded-full border-blue-500/20 border-t-blue-500',
          sizes[size]
        )}
        role="status"
        aria-label={label}
      />
      {label && <p className="text-xs text-slate-400 tracking-wide font-medium">{label}</p>}
    </div>
  )
}
