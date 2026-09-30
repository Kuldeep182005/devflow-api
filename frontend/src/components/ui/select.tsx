import React from 'react'
import { cn } from '@/lib/utils'

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: string
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, children, ...props }, ref) => {
    return (
      <div className="w-full">
        <select
          ref={ref}
          className={cn(
            'flex h-10 w-full rounded-md border bg-[#0e131d] px-3.5 py-2 text-sm text-slate-100 shadow-sm transition-colors',
            'focus:outline-none focus:ring-2 focus:ring-blue-500/80 focus:border-blue-500',
            'disabled:cursor-not-allowed disabled:opacity-50',
            error
              ? 'border-red-500/80 focus:ring-red-500 focus:border-red-500 text-red-100'
              : 'border-slate-800 focus:border-blue-500',
            className
          )}
          aria-invalid={error ? 'true' : undefined}
          {...props}
        >
          {children}
        </select>
        {error && (
          <p className="mt-1 text-xs text-red-400 font-medium" role="alert">
            {error}
          </p>
        )}
      </div>
    )
  }
)

Select.displayName = 'Select'
