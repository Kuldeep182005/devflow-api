import React from 'react'
import {
  GitBranch,
  GitCommit,
  GitPullRequest,
  ExternalLink,
  AlertTriangle,
  RotateCw,
  FolderGit2,
  Clock,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { GithubMutationStatusBadge } from '@/components/github/GithubMutationStatusBadge'
import { getSafeGithubPrUrl } from '@/lib/urls'
import { cn } from '@/lib/utils'
import type { GithubMutationResponse } from '@/types/api'

interface GithubMutationCardProps {
  mutation: GithubMutationResponse
  repositoryFullName?: string
  onRetry?: () => void
  canRetry?: boolean
  isRetrying?: boolean
  className?: string
}

export const GithubMutationCard: React.FC<GithubMutationCardProps> = ({
  mutation,
  repositoryFullName,
  onRetry,
  canRetry = false,
  isRetrying = false,
  className,
}) => {
  const safePrUrl = getSafeGithubPrUrl(mutation.pullRequestUrl)

  const isFailed = mutation.status === 'FAILED'
  const isTerminal = mutation.status === 'SUCCEEDED' || mutation.status === 'FAILED'

  const formattedCreatedAt = new Date(mutation.createdAt).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  const formattedUpdatedAt = mutation.updatedAt
    ? new Date(mutation.updatedAt).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : null

  return (
    <div
      className={cn(
        'rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4',
        className
      )}
    >
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-800/60">
        <div className="flex items-center gap-2.5">
          <div className="h-8 w-8 rounded-lg bg-emerald-600/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <GitPullRequest className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
              GitHub Remote Mutation
            </h3>
            <p className="text-[11px] text-slate-400">
              Consequential delivery of approved changes to target GitHub repository
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5 self-start sm:self-auto">
          <GithubMutationStatusBadge status={mutation.status} />
          {!isTerminal && (
            <span className="inline-flex items-center gap-1.5 text-[10px] text-blue-400 bg-blue-950/40 px-2 py-0.5 rounded-full border border-blue-500/30">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-400 animate-ping" />
              Live Sync (3s)
            </span>
          )}
        </div>
      </div>

      {/* Mutation Details Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
        {/* Remote Branch */}
        <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1">
          <span className="text-[10px] text-slate-400">Remote Branch</span>
          <div className="flex items-center gap-1.5 font-mono text-slate-200">
            <GitBranch className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            <span className="truncate" title={mutation.branchName || 'Not created yet'}>
              {mutation.branchName || 'Pending branch'}
            </span>
          </div>
        </div>

        {/* Base Revision */}
        <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1">
          <span className="text-[10px] text-slate-400">Base Revision</span>
          <div className="flex items-center gap-1.5 font-mono text-slate-200">
            <FolderGit2 className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            <span className="truncate">
              {mutation.baseRevision || repositoryFullName || 'main'}
            </span>
          </div>
        </div>

        {/* Commit SHA */}
        <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1">
          <span className="text-[10px] text-slate-400">Commit SHA</span>
          <div className="flex items-center gap-1.5 font-mono text-slate-200">
            <GitCommit className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            {mutation.commitSha ? (
              <span className="font-mono text-slate-100" title={mutation.commitSha}>
                {mutation.commitSha.slice(0, 7)}
              </span>
            ) : (
              <span className="text-slate-400 italic">Pending commit</span>
            )}
          </div>
        </div>

        {/* Pull Request */}
        <div className="p-3 rounded-lg border border-slate-800/60 bg-slate-900/40 space-y-1">
          <span className="text-[10px] text-slate-400">Pull Request</span>
          <div className="flex items-center gap-1.5 font-mono">
            <GitPullRequest className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            {mutation.pullRequestNumber && safePrUrl ? (
              <a
                href={safePrUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 font-semibold transition-colors"
                title={`Open Pull Request #${mutation.pullRequestNumber} on GitHub`}
              >
                PR #{mutation.pullRequestNumber}
                <ExternalLink className="h-3 w-3" />
              </a>
            ) : mutation.pullRequestNumber ? (
              <span className="text-slate-200 font-semibold">
                PR #{mutation.pullRequestNumber}
              </span>
            ) : (
              <span className="text-slate-400 italic">Pending PR opening</span>
            )}
          </div>
        </div>
      </div>

      {/* Failure Callout & Retry Action */}
      {isFailed && (
        <div className="p-4 rounded-lg border border-red-500/40 bg-red-950/20 text-red-200 space-y-2">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="flex items-center gap-2 font-semibold text-xs text-red-300">
              <AlertTriangle className="h-4 w-4 text-red-400 shrink-0" />
              <span>GitHub Mutation Execution Failed</span>
              {mutation.failureCode && (
                <Badge variant="danger" className="text-[10px] font-mono ml-1">
                  {mutation.failureCode}
                </Badge>
              )}
            </div>

            {onRetry && canRetry && (
              <Button
                variant="outline"
                size="sm"
                onClick={onRetry}
                disabled={isRetrying}
                isLoading={isRetrying}
                className="border-red-500/40 text-red-300 hover:bg-red-950/40 hover:text-red-200 text-xs shrink-0"
              >
                <RotateCw className="h-3.5 w-3.5 mr-1.5" />
                Retry Mutation
              </Button>
            )}
          </div>

          {mutation.failureMessage && (
            <p className="text-xs text-red-200/90 font-mono leading-relaxed pl-6">
              {mutation.failureMessage}
            </p>
          )}
        </div>
      )}

      {/* Timestamps Footer */}
      <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-800/40 text-[11px] text-slate-400 font-mono">
        <div className="flex items-center gap-1.5">
          <Clock className="h-3 w-3 text-slate-400" />
          <span>Dispatched {formattedCreatedAt}</span>
        </div>
        {formattedUpdatedAt && (
          <span>Last updated {formattedUpdatedAt}</span>
        )}
      </div>
    </div>
  )
}
