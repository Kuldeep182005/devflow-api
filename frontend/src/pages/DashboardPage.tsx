import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Building2,
  FolderGit2,
  CheckSquare2,
  ShieldAlert,
  ArrowRight,
  CheckCircle2,
  Play,
} from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { EmptyState } from '@/components/common/EmptyState'
import { CreateOrganizationDialog } from '@/components/organizations/CreateOrganizationDialog'
import { CreateTaskDialog } from '@/components/tasks/CreateTaskDialog'
import { ConnectGithubDialog } from '@/components/repositories/ConnectGithubDialog'
import { DashboardMetricCard } from '@/components/dashboard/DashboardMetricCard'
import { RecentActivity, type EnrichedActivityEvent } from '@/components/dashboard/RecentActivity'
import { useAuth } from '@/hooks/useAuth'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { repositoriesApi } from '@/api/repositories'
import { tasksApi } from '@/api/tasks'
import { approvalsApi } from '@/api/approvals'
import { formatDate } from '@/lib/utils'

export const DashboardPage: React.FC = () => {
  const { session } = useAuth()
  const {
    organizations,
    activeOrganization,
    setActiveOrganizationId,
    isLoading: isLoadingOrgs,
    error: orgsError,
    refetchOrganizations,
  } = useActiveOrganization()

  const [isCreateOrgOpen, setIsCreateOrgOpen] = useState(false)
  const [isCreateTaskOpen, setIsCreateTaskOpen] = useState(false)
  const [isConnectRepoOpen, setIsConnectRepoOpen] = useState(false)

  const activeOrgId = activeOrganization?.id || ''

  // Query repositories for the active organization
  const {
    data: repositoriesPage,
    isLoading: isLoadingRepos,
  } = useQuery({
    queryKey: ['organizations', activeOrgId, 'repositories', { size: 100 }],
    queryFn: () => repositoriesApi.list(activeOrgId, { size: 100 }),
    enabled: !!activeOrgId,
  })

  // Query recent tasks for the active organization
  const {
    data: tasksPage,
    isLoading: isLoadingTasks,
  } = useQuery({
    queryKey: ['organizations', activeOrgId, 'tasks', { size: 100 }],
    queryFn: () => tasksApi.list(activeOrgId, { size: 100 }),
    enabled: !!activeOrgId,
  })

  // Query approvals for the active organization
  const {
    data: approvalsPage,
    isLoading: isLoadingApprovals,
  } = useQuery({
    queryKey: ['organizations', activeOrgId, 'approvals', { size: 100 }],
    queryFn: () => approvalsApi.list(activeOrgId, { size: 100 }),
    enabled: !!activeOrgId,
  })

  // Fetch authoritative events for recent tasks in parallel
  const recentTasks = (tasksPage?.content || []).slice(0, 5)

  const {
    data: topEvents = [],
    isLoading: isEventsLoading,
  } = useQuery({
    queryKey: [
      'organizations',
      activeOrgId,
      'dashboard-recent-events',
      recentTasks.map((t) => t.id),
    ],
    queryFn: async (): Promise<EnrichedActivityEvent[]> => {
      if (recentTasks.length === 0) return []
      const eventPages = await Promise.all(
        recentTasks.map(async (task) => {
          try {
            const page = await tasksApi.events(activeOrgId, task.id, { size: 5 })
            return (page.content || []).map((evt) => ({
              id: `${task.id}-${evt.id}`,
              taskId: task.id,
              taskTitle: task.title,
              eventType: evt.eventType,
              occurredAt: evt.occurredAt,
              actorSource: evt.actorSource,
              previousState: evt.previousState,
              newState: evt.newState,
            }))
          } catch {
            return []
          }
        })
      )
      const all = eventPages.flat()
      all.sort(
        (a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime()
      )
      return all.slice(0, 8)
    },
    enabled: !!activeOrgId && recentTasks.length > 0,
  })

  // Calculate authoritative metrics
  const allTasks = tasksPage?.content || []
  const activeTasksCount = allTasks.filter(
    (t) => t.status === 'RUNNING' || t.status === 'QUEUED' || t.status === 'WAITING_FOR_APPROVAL'
  ).length

  const allApprovals = approvalsPage?.content || []
  const pendingApprovalsCount = allApprovals.filter((a) => a.status === 'PENDING').length

  const completedTasksCount = allTasks.filter((t) => t.status === 'SUCCEEDED').length
  const repositoriesCount = repositoriesPage?.totalElements ?? repositoriesPage?.content?.length ?? 0

  return (
    <div className="p-6 md:p-8 space-y-8 max-w-7xl mx-auto w-full">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold text-white tracking-tight">Engineering Overview</h1>
            <Badge variant="success">API Connected</Badge>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Signed in as <span className="text-slate-200 font-medium">{session?.displayName || session?.email}</span>. Autonomous software verification workspace.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setIsCreateOrgOpen(true)}
            className="text-xs text-slate-300"
          >
            <Building2 className="h-3.5 w-3.5 mr-1.5" />
            New Organization
          </Button>

          {activeOrganization && (
            <Button
              size="sm"
              variant="primary"
              onClick={() => setIsCreateTaskOpen(true)}
              className="bg-blue-600 hover:bg-blue-500 text-white"
            >
              <Play className="h-3.5 w-3.5 mr-1.5" />
              New Task
            </Button>
          )}
        </div>
      </div>

      {isLoadingOrgs && <LoadingSpinner label="Loading engineering workspace..." />}

      {orgsError && (
        <ErrorMessage
          title="Could not load organizations"
          message={orgsError.message || 'Backend connection error.'}
          onRetry={refetchOrganizations}
        />
      )}

      {!isLoadingOrgs && !orgsError && (
        organizations.length === 0 ? (
          <EmptyState
            title="Welcome to DevFlow"
            description="Create your first organization to configure GitHub repositories, dispatch autonomous tasks, and review verified code changes."
            icon={<Building2 className="h-7 w-7" />}
            actionLabel="Create Organization"
            onAction={() => setIsCreateOrgOpen(true)}
          />
        ) : (
          <div className="space-y-8">
            {/* Active Organization Spotlight */}
            {activeOrganization && (
              <div className="space-y-6">
                <Card className="border-blue-500/40 bg-gradient-to-r from-blue-950/20 via-[#111622] to-[#111622]">
                  <CardHeader className="pb-3 border-b-0">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div className="h-10 w-10 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
                          <Building2 className="h-5 w-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <CardTitle className="text-lg">{activeOrganization.name}</CardTitle>
                            <Badge variant="neutral" className="font-mono">{activeOrganization.slug}</Badge>
                            <Badge variant="success" className="text-[10px] flex items-center gap-1">
                              <CheckCircle2 className="h-3 w-3" />
                              Active Workspace
                            </Badge>
                          </div>
                          <CardDescription className="font-mono text-[11px] mt-0.5">
                            ID: {activeOrganization.id} • Created: {formatDate(activeOrganization.createdAt)}
                          </CardDescription>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setIsConnectRepoOpen(true)}
                          className="text-xs"
                        >
                          <FolderGit2 className="h-3.5 w-3.5 mr-1.5" />
                          Connect Repo
                        </Button>
                        <Link to={`/organizations/${activeOrganization.id}`}>
                          <Button size="sm" variant="secondary" className="text-xs">
                            Organization Settings
                          </Button>
                        </Link>
                      </div>
                    </div>
                  </CardHeader>

                  <CardContent className="pt-2">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-3 border-t border-slate-800/80">
                      <Link
                        to={`/organizations/${activeOrganization.id}/repositories`}
                        className="flex items-center justify-between p-3 rounded-lg border border-slate-800/80 bg-[#0e131d] hover:border-slate-700 hover:bg-slate-800/30 transition-colors"
                      >
                        <div className="flex items-center gap-2.5">
                          <FolderGit2 className="h-4 w-4 text-blue-400" />
                          <div>
                            <p className="text-xs font-semibold text-slate-200">Repositories</p>
                            <p className="text-[10px] text-slate-400">
                              {isLoadingRepos ? 'Loading...' : `${repositoriesCount} connected`}
                            </p>
                          </div>
                        </div>
                        <ArrowRight className="h-3.5 w-3.5 text-slate-500" />
                      </Link>

                      <Link
                        to={`/organizations/${activeOrganization.id}/tasks`}
                        className="flex items-center justify-between p-3 rounded-lg border border-slate-800/80 bg-[#0e131d] hover:border-slate-700 hover:bg-slate-800/30 transition-colors"
                      >
                        <div className="flex items-center gap-2.5">
                          <CheckSquare2 className="h-4 w-4 text-amber-400" />
                          <div>
                            <p className="text-xs font-semibold text-slate-200">AI Tasks</p>
                            <p className="text-[10px] text-slate-400">
                              {isLoadingTasks ? 'Loading...' : `${allTasks.length} recorded`}
                            </p>
                          </div>
                        </div>
                        <ArrowRight className="h-3.5 w-3.5 text-slate-500" />
                      </Link>

                      <Link
                        to={`/organizations/${activeOrganization.id}/approvals`}
                        className="flex items-center justify-between p-3 rounded-lg border border-slate-800/80 bg-[#0e131d] hover:border-slate-700 hover:bg-slate-800/30 transition-colors"
                      >
                        <div className="flex items-center gap-2.5">
                          <ShieldAlert className="h-4 w-4 text-emerald-400" />
                          <div>
                            <p className="text-xs font-semibold text-slate-200">Approvals Gate</p>
                            <p className="text-[10px] text-slate-400">
                              {isLoadingApprovals ? 'Loading...' : `${pendingApprovalsCount} pending review`}
                            </p>
                          </div>
                        </div>
                        <ArrowRight className="h-3.5 w-3.5 text-slate-500" />
                      </Link>
                    </div>
                  </CardContent>
                </Card>

                {/* Authoritative Metric Cards Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                  <DashboardMetricCard
                    title="Active Tasks"
                    value={activeTasksCount}
                    description="Running or queued in worker"
                    icon={CheckSquare2}
                    to={`/organizations/${activeOrganization.id}/tasks`}
                    variant="amber"
                    isLoading={isLoadingTasks}
                  />

                  <DashboardMetricCard
                    title="Awaiting Approval"
                    value={pendingApprovalsCount}
                    description="Human authorization gate"
                    icon={ShieldAlert}
                    to={`/organizations/${activeOrganization.id}/approvals`}
                    variant="purple"
                    isLoading={isLoadingApprovals}
                  />

                  <DashboardMetricCard
                    title="Connected Repositories"
                    value={repositoriesCount}
                    description="GitHub provider repos"
                    icon={FolderGit2}
                    to={`/organizations/${activeOrganization.id}/repositories`}
                    variant="blue"
                    isLoading={isLoadingRepos}
                  />

                  <DashboardMetricCard
                    title="Completed Tasks"
                    value={completedTasksCount}
                    description="Successfully completed"
                    icon={CheckCircle2}
                    to={`/organizations/${activeOrganization.id}/tasks`}
                    variant="emerald"
                    isLoading={isLoadingTasks}
                  />
                </div>

                {/* Split Grid: Recent Activity & Security Architecture */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                  {/* Left (2 cols): Authoritative Event Activity Feed */}
                  <div className="lg:col-span-2">
                    <RecentActivity
                      events={topEvents}
                      organizationId={activeOrganization.id}
                      isLoading={isLoadingTasks || isEventsLoading}
                    />
                  </div>

                  {/* Right (1 col): DevFlow Security & Workflow Architecture */}
                  <div className="lg:col-span-1 space-y-4">
                    <div className="rounded-xl border border-slate-800/80 bg-[#0e131d] p-5 shadow-xs space-y-4">
                      <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
                        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-300 font-mono">
                          DevFlow Architecture
                        </h3>
                        <Badge variant="purple" className="text-[10px]">
                          Security Invariant
                        </Badge>
                      </div>

                      <p className="text-xs text-slate-400 leading-relaxed">
                        DevFlow enforces a strict security boundary: AI-formulated patches are verified in isolated Docker sandboxes and cannot mutate remote GitHub repositories without explicit human authorization.
                      </p>

                      <div className="space-y-2.5 pt-1 text-xs">
                        <div className="p-2.5 rounded-lg border border-slate-800/60 bg-slate-900/40 flex items-start gap-2.5">
                          <span className="font-mono text-blue-400 text-[10px] font-bold mt-0.5">1</span>
                          <div>
                            <p className="font-semibold text-slate-200">AI Formulation</p>
                            <p className="text-[11px] text-slate-400">Autonomous worker analyzes task & creates patch</p>
                          </div>
                        </div>

                        <div className="p-2.5 rounded-lg border border-slate-800/60 bg-slate-900/40 flex items-start gap-2.5">
                          <span className="font-mono text-purple-400 text-[10px] font-bold mt-0.5">2</span>
                          <div>
                            <p className="font-semibold text-slate-200">Sandbox Verification</p>
                            <p className="text-[11px] text-slate-400">Isolated compile and test suite execution</p>
                          </div>
                        </div>

                        <div className="p-2.5 rounded-lg border border-purple-500/30 bg-purple-950/20 flex items-start gap-2.5">
                          <span className="font-mono text-purple-300 text-[10px] font-bold mt-0.5">3</span>
                          <div>
                            <p className="font-semibold text-purple-200">Human Authorization Gate</p>
                            <p className="text-[11px] text-purple-300/80">Owner/Admin explicitly reviews & approves proposal</p>
                          </div>
                        </div>

                        <div className="p-2.5 rounded-lg border border-slate-800/60 bg-slate-900/40 flex items-start gap-2.5">
                          <span className="font-mono text-emerald-400 text-[10px] font-bold mt-0.5">4</span>
                          <div>
                            <p className="font-semibold text-slate-200">GitHub Remote Delivery</p>
                            <p className="text-[11px] text-slate-400">Remote branch creation, commit push, and PR opening</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Organizations Directory */}
            <div className="space-y-4 pt-4 border-t border-slate-800/60">
              <div className="flex items-center justify-between">
                <h2 className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-mono">
                  Your Organizations ({organizations.length})
                </h2>
                <Link to="/organizations" className="text-xs text-blue-400 hover:text-blue-300 font-medium">
                  View all →
                </Link>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {organizations.map((org) => {
                  const isActive = activeOrganization?.id === org.id

                  return (
                    <Card
                      key={org.id}
                      className={isActive ? 'border-blue-500/40' : 'hover:border-slate-700/80'}
                    >
                      <CardHeader className="pb-3">
                        <div className="flex items-start justify-between">
                          <CardTitle className="text-sm">{org.name}</CardTitle>
                          <Badge variant="neutral" className="text-[10px] font-mono">{org.slug}</Badge>
                        </div>
                        <CardDescription className="text-[11px] font-mono">
                          {formatDate(org.createdAt)}
                        </CardDescription>
                      </CardHeader>

                      <CardContent className="pt-2 flex items-center justify-between border-t border-slate-800/60 text-xs">
                        <Link
                          to={`/organizations/${org.id}`}
                          className="text-blue-400 hover:text-blue-300 font-medium inline-flex items-center gap-1"
                        >
                          Details <ArrowRight className="h-3 w-3" />
                        </Link>
                        {!isActive && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setActiveOrganizationId(org.id)}
                            className="text-[11px] h-6 px-2 text-slate-400 hover:text-white"
                          >
                            Set Active
                          </Button>
                        )}
                      </CardContent>
                    </Card>
                  )
                })}
              </div>
            </div>
          </div>
        )
      )}

      {/* Dialogs */}
      <CreateOrganizationDialog
        isOpen={isCreateOrgOpen}
        onClose={() => setIsCreateOrgOpen(false)}
      />

      {activeOrganization && (
        <>
          <CreateTaskDialog
            isOpen={isCreateTaskOpen}
            onClose={() => setIsCreateTaskOpen(false)}
            organizationId={activeOrganization.id}
          />
          <ConnectGithubDialog
            isOpen={isConnectRepoOpen}
            onClose={() => setIsConnectRepoOpen(false)}
            organizationId={activeOrganization.id}
            organizationSlug={activeOrganization.slug}
          />
        </>
      )}
    </div>
  )
}
