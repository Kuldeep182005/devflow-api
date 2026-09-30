import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { Building2, Plus, ArrowRight, CheckCircle2 } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { EmptyState } from '@/components/common/EmptyState'
import { CreateOrganizationDialog } from '@/components/organizations/CreateOrganizationDialog'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { formatDate } from '@/lib/utils'

export const OrganizationsPage: React.FC = () => {
  const {
    organizations,
    activeOrganizationId,
    setActiveOrganizationId,
    isLoading,
    error,
    refetchOrganizations,
  } = useActiveOrganization()

  const [isCreateOpen, setIsCreateOpen] = useState(false)

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-800/80 pb-5">
        <div>
          <h1 className="text-xl font-bold text-white tracking-tight">Organizations</h1>
          <p className="text-xs text-slate-400 mt-1">
            Workspaces managing your team members, repositories, and automated AI proposals.
          </p>
        </div>

        <Button
          size="sm"
          variant="primary"
          onClick={() => setIsCreateOpen(true)}
        >
          <Plus className="h-4 w-4 mr-1.5" />
          New Organization
        </Button>
      </div>

      {/* Loading & Error States */}
      {isLoading && <LoadingSpinner label="Fetching organizations..." />}

      {error && (
        <ErrorMessage
          title="Could not load organizations"
          message={error.message || 'Failed to connect to organization service'}
          onRetry={refetchOrganizations}
        />
      )}

      {/* Organization Grid */}
      {!isLoading && !error && (
        organizations.length === 0 ? (
          <EmptyState
            title="No organizations connected"
            description="Create your first organization to connect GitHub repositories and trigger automated engineering tasks."
            icon={<Building2 className="h-6 w-6" />}
            actionLabel="Create Organization"
            onAction={() => setIsCreateOpen(true)}
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {organizations.map((org) => {
              const isActive = activeOrganizationId === org.id

              return (
                <Card
                  key={org.id}
                  className={`flex flex-col justify-between transition-all ${
                    isActive ? 'border-blue-500/50 shadow-blue-950/20 shadow-lg' : 'hover:border-slate-700/80'
                  }`}
                >
                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <CardTitle className="truncate">{org.name}</CardTitle>
                        <p className="text-xs font-mono text-slate-400 mt-1 truncate">{org.slug}</p>
                      </div>
                      {isActive ? (
                        <Badge variant="success" className="shrink-0 flex items-center gap-1">
                          <CheckCircle2 className="h-3 w-3" />
                          Active
                        </Badge>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setActiveOrganizationId(org.id)}
                          className="text-[11px] h-6 px-2 text-slate-400 hover:text-white"
                        >
                          Set Active
                        </Button>
                      )}
                    </div>
                  </CardHeader>

                  <CardContent className="pt-3 space-y-4">
                    <div className="space-y-1.5 text-xs text-slate-400">
                      <div className="flex justify-between">
                        <span>Created:</span>
                        <span className="text-slate-300 font-mono text-[11px]">
                          {formatDate(org.createdAt)}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span>ID:</span>
                        <span className="text-slate-400 font-mono text-[10px] truncate max-w-[150px]">
                          {org.id}
                        </span>
                      </div>
                    </div>

                    <div className="pt-3 border-t border-slate-800/60 flex items-center justify-between">
                      <Link
                        to={`/organizations/${org.id}`}
                        className="text-xs text-blue-400 hover:text-blue-300 font-medium inline-flex items-center gap-1"
                      >
                        Manage & Members
                        <ArrowRight className="h-3 w-3" />
                      </Link>

                      <Link
                        to={`/organizations/${org.id}/tasks`}
                        className="text-xs text-slate-400 hover:text-slate-200"
                      >
                        Tasks
                      </Link>
                    </div>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        )
      )}

      {/* Modal Dialog */}
      <CreateOrganizationDialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
    </div>
  )
}
