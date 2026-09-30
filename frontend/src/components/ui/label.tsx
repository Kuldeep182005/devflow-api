import React from 'react'
import { cn } from '@/lib/utils'

export interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {
  required?: boolean
}

export const Label: React.FC<LabelProps> = ({ className, required, children, ...props }) => {
  return (
    <label
      className={cn('block text-xs font-medium text-slate-300 mb-1.5', className)}
      {...props}
    >
      {children}
      {required && <span className="text-red-400 ml-1" aria-hidden="true">*</span>}
    </label>
  )
}
