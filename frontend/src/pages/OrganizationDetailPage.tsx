import React, { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Building2,
  FolderGit2,
  CheckSquare2,
  ShieldAlert,
  UserPlus,
  Trash2,
  Calendar,
  Fingerprint,
} from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Dialog } from '@/components/ui/dialog'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { EmptyState } from '@/components/common/EmptyState'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'
import { useAuth } from '@/hooks/useAuth'
import { formatDate } from '@/lib/utils'
import type { OrganizationRole, OrganizationMemberResponse } from '@/types/api'

export const OrganizationDetailPage: React.FC = () => {
  const { organizationId } = useParams<{ organizationId: string }>()
  const { session } = useAuth()
  const queryClient = useQueryClient()

  // Modals state
  const [isAddMemberOpen, setIsAddMemberOpen] = useState(false)
  const [memberToRemove, setMemberToRemove] = useState<OrganizationMemberResponse | null>(null)

  // Add member form state
  const [newUserId, setNewUserId] = useState('')
  const [newRole, setNewRole] = useState<OrganizationRole>('MEMBER')
  const [addMemberError, setAddMemberError] = useState<string | null>(null)

  // Feedback banner state for action results/errors
  const [actionError, setActionError] = useState<{
    message: string
    code?: string
    requestId?: string
  } | null>(null)
  const [actionSuccess, setActionSuccess] = useState<string | null>(null)

  // Fetch organization details
  const {
    data: organization,
    isLoading: isOrgLoading,
    error: orgError,
    refetch: refetchOrg,
  } = useQuery({
    queryKey: ['organization', organizationId],
    queryFn: () => organizationsApi.get(organizationId!),
    enabled: !!organizationId,
  })

  // Fetch organization members
  const {
    data: membersPage,
    isLoading: isMembersLoading,
    error: membersError,
    refetch: refetchMembers,
  } = useQuery({
    queryKey: ['organization-members', organizationId],
    queryFn: () => organizationsApi.listMembers(organizationId!, { page: 0, size: 50 }),
    enabled: !!organizationId,
  })

  // Add Member Mutation
  const addMemberMutation = useMutation({
    mutationFn: (data: { userId: string; role: OrganizationRole }) =>
      organizationsApi.addMember(organizationId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organization-members', organizationId] })
      setIsAddMemberOpen(false)
      setNewUserId('')
      setNewRole('MEMBER')
      setAddMemberError(null)
      setActionSuccess('Member successfully added to organization.')
      setTimeout(() => setActionSuccess(null), 4000)
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.isForbidden) {
          setAddMemberError('You do not have permission to add members to this organization.')
        } else if (err.isConflict) {
          setAddMemberError('This user is already a member of this organization.')
        } else {
          setAddMemberError(err.message)
        }
      } else {
        setAddMemberError('Failed to add member. Please verify the user ID.')
      }
    },
  })

  // Update Member Role Mutation
  const updateRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrganizationRole }) =>
      organizationsApi.updateMember(organizationId!, userId, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organization-members', organizationId] })
      setActionSuccess('Member role successfully updated.')
      setActionError(null)
      setTimeout(() => setActionSuccess(null), 4000)
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.isForbidden) {
          setActionError({
            message: 'You do not have permission to modify roles in this organization.',
            code: err.code,
            requestId: err.requestId,
          })
        } else {
          setActionError({
            message: err.message,
            code: err.code,
            requestId: err.requestId,
          })
        }
      } else {
        setActionError({ message: 'Failed to update member role.' })
      }
    },
  })

  // Remove Member Mutation
  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => organizationsApi.removeMember(organizationId!, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organization-members', organizationId] })
      setMemberToRemove(null)
      setActionSuccess('Member was removed from organization.')
      setActionError(null)
      setTimeout(() => setActionSuccess(null), 4000)
    },
    onError: (err: unknown) => {
      if (err instanceof ApiClientError) {
        if (err.isForbidden) {
          setActionError({
            message: 'You do not have permission to remove members from this organization.',
            code: err.code,
            requestId: err.requestId,
          })
        } else {
          setActionError({
            message: err.message,
            code: err.code,
            requestId: err.requestId,
          })
        }
      } else {
        setActionError({ message: 'Failed to remove member.' })
      }
      setMemberToRemove(null)
    },
  })

  const handleAddMemberSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setAddMemberError(null)

    const trimmedId = newUserId.trim()
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    if (!trimmedId) {
      setAddMemberError('User UUID is required')
      return
    }
    if (!uuidRegex.test(trimmedId)) {
      setAddMemberError('Please enter a valid UUID format (e.g. 12345678-1234-1234-1234-123456789abc)')
      return
    }

    addMemberMutation.mutate({ userId: trimmedId, role: newRole })
  }

  // Derive current user's role from members list
  const currentMember = membersPage?.content?.find((m) => m.userId === session?.userId)
  const currentUserRole = currentMember?.role

  if (isOrgLoading) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full">
        <LoadingSpinner size="lg" label="Loading organization details..." />
      </div>
    )
  }

  if (orgError || !organization) {
    return (
      <div className="p-8 max-w-7xl mx-auto w-full space-y-4">
        <Link to="/organizations" className="text-xs text-blue-400 hover:text-blue-300">
          ← Back to Organizations
        </Link>
        <ErrorMessage
          title="Organization not found"
          message={orgError instanceof Error ? orgError.message : 'The requested organization does not exist.'}
          onRetry={() => refetchOrg()}
        />
      </div>
    )
  }

  const roleVariants: Record<OrganizationRole, 'purple' | 'info' | 'default' | 'neutral'> = {
    OWNER: 'purple',
    ADMIN: 'info',
    MEMBER: 'default',
    VIEWER: 'neutral',
  }

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
      {/* Breadcrumb Navigation */}
      <div className="flex items-center gap-2 text-xs text-slate-400">
        <Link to="/organizations" className="hover:text-slate-200">
          Organizations
        </Link>
        <span>/</span>
        <span className="text-slate-200 font-medium">{organization.name}</span>
      </div>

      {/* Organization Header Bar */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
              <Building2 className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-bold text-white tracking-tight">{organization.name}</h1>
                <Badge variant="neutral" className="font-mono">{organization.slug}</Badge>
                {currentUserRole && (
                  <Badge variant={roleVariants[currentUserRole]}>
                    Your Role: {currentUserRole}
                  </Badge>
                )}
              </div>
              <div className="flex items-center gap-4 text-xs text-slate-400 mt-1">
                <span className="flex items-center gap-1">
                  <Fingerprint className="h-3.5 w-3.5 text-slate-500" />
                  <span className="font-mono text-[11px]">{organization.id}</span>
                </span>
                <span className="flex items-center gap-1">
                  <Calendar className="h-3.5 w-3.5 text-slate-500" />
                  Created {formatDate(organization.createdAt)}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Action Shortcuts */}
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="primary"
            onClick={() => setIsAddMemberOpen(true)}
          >
            <UserPlus className="h-3.5 w-3.5 mr-1.5" />
            Add Member
          </Button>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex items-center gap-1 border-b border-slate-800/80 pb-px text-xs font-medium">
        <button className="px-3.5 py-2 text-blue-400 border-b-2 border-blue-500 font-semibold flex items-center gap-2">
          <Building2 className="h-3.5 w-3.5" />
          Overview & Members
        </button>
        <Link
          to={`/organizations/${organization.id}/repositories`}
          className="px-3.5 py-2 text-slate-400 hover:text-slate-200 transition-colors flex items-center gap-2"
        >
          <FolderGit2 className="h-3.5 w-3.5" />
          Repositories
        </Link>
        <Link
          to={`/organizations/${organization.id}/tasks`}
          className="px-3.5 py-2 text-slate-400 hover:text-slate-200 transition-colors flex items-center gap-2"
        >
          <CheckSquare2 className="h-3.5 w-3.5" />
          Tasks
        </Link>
        <Link
          to={`/organizations/${organization.id}/approvals`}
          className="px-3.5 py-2 text-slate-400 hover:text-slate-200 transition-colors flex items-center gap-2"
        >
          <ShieldAlert className="h-3.5 w-3.5" />
          Approvals
        </Link>
      </div>

      {/* Feedback Messages */}
      {actionSuccess && (
        <div className="p-3 bg-emerald-950/40 border border-emerald-500/30 text-emerald-300 text-xs rounded-lg">
          {actionSuccess}
        </div>
      )}

      {actionError && (
        <ErrorMessage
          title="Action failed"
          message={actionError.message}
          code={actionError.code}
          requestId={actionError.requestId}
        />
      )}

      {/* Members Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-200 tracking-wide">
            Team Members ({membersPage?.totalElements ?? membersPage?.content?.length ?? 0})
          </h2>
        </div>

        {isMembersLoading && <LoadingSpinner label="Loading organization members..." />}

        {membersError && (
          <ErrorMessage
            title="Failed to load members"
            message={membersError instanceof Error ? membersError.message : 'Could not fetch member list'}
            onRetry={() => refetchMembers()}
          />
        )}

        {!isMembersLoading && !membersError && membersPage && (
          membersPage.content.length === 0 ? (
            <EmptyState
              title="No members listed"
              description="Invite team members to collaborate on AI software workflows."
              icon={<UserPlus className="h-6 w-6" />}
              actionLabel="Add Member"
              onAction={() => setIsAddMemberOpen(true)}
            />
          ) : (
            <Card className="overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#0e131d] text-slate-400 border-b border-slate-800/80 uppercase text-[10px] tracking-wider font-mono">
                    <tr>
                      <th className="px-5 py-3">Member</th>
                      <th className="px-5 py-3">User ID</th>
                      <th className="px-5 py-3">Role</th>
                      <th className="px-5 py-3">Joined</th>
                      <th className="px-5 py-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {membersPage.content.map((member) => {
                      const isCurrentUser = member.userId === session?.userId

                      return (
                        <tr key={member.userId} className="hover:bg-slate-800/20 transition-colors">
                          {/* Member Email & Display Name */}
                          <td className="px-5 py-3.5">
                            <div>
                              <p className="font-medium text-slate-200">
                                {member.displayName || 'No display name'}
                                {isCurrentUser && (
                                  <span className="ml-1.5 text-[10px] text-blue-400 font-mono">(You)</span>
                                )}
                              </p>
                              <p className="text-[11px] text-slate-400 font-mono">{member.email}</p>
                            </div>
                          </td>

                          {/* User ID */}
                          <td className="px-5 py-3.5 font-mono text-[11px] text-slate-400">
                            {member.userId}
                          </td>

                          {/* Role Selector */}
                          <td className="px-5 py-3.5">
                            <div className="w-32">
                              <Select
                                value={member.role}
                                disabled={updateRoleMutation.isPending}
                                onChange={(e) =>
                                  updateRoleMutation.mutate({
                                    userId: member.userId,
                                    role: e.target.value as OrganizationRole,
                                  })
                                }
                                className="h-8 text-xs py-1 px-2.5"
                              >
                                <option value="OWNER">OWNER</option>
                                <option value="ADMIN">ADMIN</option>
                                <option value="MEMBER">MEMBER</option>
                                <option value="VIEWER">VIEWER</option>
                              </Select>
                            </div>
                          </td>

                          {/* Joined Date */}
                          <td className="px-5 py-3.5 text-slate-400 whitespace-nowrap">
                            {formatDate(member.createdAt)}
                          </td>

                          {/* Actions */}
                          <td className="px-5 py-3.5 text-right whitespace-nowrap">
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Remove member"
                              aria-label={`Remove ${member.email} from organization`}
                              onClick={() => setMemberToRemove(member)}
                              className="text-slate-400 hover:text-red-400 hover:bg-red-950/20 h-7 w-7 p-0 inline-flex items-center justify-center"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </Card>
          )
        )}
      </div>

      {/* Add Member Modal Dialog */}
      <Dialog
        isOpen={isAddMemberOpen}
        onClose={() => {
          setIsAddMemberOpen(false)
          setAddMemberError(null)
        }}
        title="Add Member to Organization"
        description="Grant an existing user access to this organization by their user UUID."
      >
        {addMemberError && (
          <div className="mb-4">
            <ErrorMessage title="Cannot add member" message={addMemberError} />
          </div>
        )}

        <form onSubmit={handleAddMemberSubmit} noValidate className="space-y-4">
          <div>
            <Label htmlFor="member-userId" required>
              User UUID
            </Label>
            <Input
              id="member-userId"
              type="text"
              required
              autoFocus
              placeholder="e.g. 7b70c36d-1bf9-47e2-892f-b44c0fbceee2"
              value={newUserId}
              disabled={addMemberMutation.isPending}
              onChange={(e) => setNewUserId(e.target.value)}
            />
            <p className="mt-1 text-[11px] text-slate-500">
              Must be a valid UUID of an already registered DevFlow user.
            </p>
          </div>

          <div>
            <Label htmlFor="member-role" required>
              Organization Role
            </Label>
            <Select
              id="member-role"
              value={newRole}
              disabled={addMemberMutation.isPending}
              onChange={(e) => setNewRole(e.target.value as OrganizationRole)}
            >
              <option value="VIEWER">VIEWER (Read-only access)</option>
              <option value="MEMBER">MEMBER (Standard engineer)</option>
              <option value="ADMIN">ADMIN (Workspace management)</option>
              <option value="OWNER">OWNER (Full administrator)</option>
            </Select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800/80">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setIsAddMemberOpen(false)}
              disabled={addMemberMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={addMemberMutation.isPending}
              disabled={addMemberMutation.isPending}
            >
              Add Member
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Confirm Member Removal Dialog */}
      <Dialog
        isOpen={!!memberToRemove}
        onClose={() => setMemberToRemove(null)}
        title="Confirm Member Removal"
        description="Are you sure you want to remove this user from the organization?"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-300">
            User <span className="font-semibold text-white">{memberToRemove?.displayName || memberToRemove?.email}</span> ({memberToRemove?.email}) will immediately lose access to this organization's repositories, tasks, and approvals.
          </p>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800/80">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setMemberToRemove(null)}
              disabled={removeMemberMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="danger"
              size="sm"
              isLoading={removeMemberMutation.isPending}
              disabled={removeMemberMutation.isPending}
              onClick={() => {
                if (memberToRemove) {
                  removeMemberMutation.mutate(memberToRemove.userId)
                }
              }}
            >
              Remove Member
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  )
}
