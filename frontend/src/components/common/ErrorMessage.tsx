import React from 'react'
import { AlertCircle, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface ErrorMessageProps {
  title?: string
  message: string
  requestId?: string
  code?: string
  onRetry?: () => void
  className?: string
}

export const ErrorMessage: React.FC<ErrorMessageProps> = ({
  title = 'Something went wrong',
  message,
  requestId,
  code,
  onRetry,
  className,
}) => {
  return (
    <div
      role="alert"
      className={cn(
        'rounded-lg border border-red-500/20 bg-red-950/20 p-5 text-red-200',
        className
      )}
    >
      <div className="flex items-start gap-3">
        <AlertCircle className="h-5 w-5 text-red-400 shrink-0 mt-0.5" />
        <div className="flex-1 space-y-1">
          <h4 className="text-sm font-semibold text-red-300">{title}</h4>
          <p className="text-xs text-red-200/80 leading-relaxed">{message}</p>
          {(requestId || code) && (
            <div className="mt-2 flex flex-wrap gap-2 text-[10px] text-red-400/70 font-mono">
              {code && <span>Code: {code}</span>}
              {requestId && <span>Ref: {requestId}</span>}
            </div>
          )}
          {onRetry && (
            <div className="pt-2">
              <Button
                variant="outline"
                size="sm"
                onClick={onRetry}
                className="border-red-500/30 text-red-300 hover:bg-red-900/30 hover:text-white"
              >
                <RefreshCw className="h-3 w-3 mr-1" />
                Retry
              </Button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
