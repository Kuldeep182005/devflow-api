// DevFlow API - Strict TypeScript Contracts matching Spring Boot Backend DTOs

// ==========================================
// Generic API Envelopes & Errors
// ==========================================

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiError {
  code: string
  message: string
  timestamp: string
  requestId: string
  violations?: FieldViolation[]
}

export interface HealthResponse {
  status: string
  checkedAt: string
}

// ==========================================
// Authentication
// ==========================================

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  displayName?: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: string
  email: string
  displayName: string
}

export interface StoredUserSession {
  accessToken: string
  tokenType: string
  expiresAt: number // timestamp in ms
  userId: string
  email: string
  displayName: string
}

// ==========================================
// Organizations & Members
// ==========================================

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'

export interface OrganizationResponse {
  id: string
  name: string
  slug: string
  createdAt: string
  updatedAt: string
}

export interface CreateOrganizationRequest {
  name: string
  slug: string
}

export interface OrganizationMemberResponse {
  organizationId: string
  userId: string
  email: string
  displayName: string
  role: OrganizationRole
  createdAt: string
}

export interface AddOrganizationMemberRequest {
  userId: string
  role: OrganizationRole
}

export interface UpdateOrganizationMemberRequest {
  role: OrganizationRole
}

// ==========================================
// Repositories
// ==========================================

export type RepositoryProvider = 'GITHUB'

export interface RepositoryResponse {
  id: string
  organizationId: string
  provider: RepositoryProvider
  externalId: string
  fullName: string
  defaultBranch: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateRepositoryRequest {
  provider: RepositoryProvider
  externalId: string
  fullName: string
  defaultBranch: string
}

export interface ConnectRepositoryRequest {
  provider: RepositoryProvider
  owner?: string
  name?: string
  repository?: string
  externalRepository?: string
  installationId?: number
}

// ==========================================
// Tasks & Events
// ==========================================

export type TaskStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_FOR_APPROVAL'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export type TaskSourceType = 'MANUAL' | 'GITHUB_ISSUE'

export type TaskActorSource = 'USER' | 'WORKER' | 'SYSTEM'

export type TaskEventType =
  | 'TASK_CREATED'
  | 'TASK_STARTED'
  | 'TASK_APPROVAL_REQUIRED'
  | 'TASK_RESUMED'
  | 'TASK_SUCCEEDED'
  | 'TASK_FAILED'
  | 'TASK_CANCELLED'
  | 'APPROVAL_REQUESTED'
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_REJECTED'
  | 'APPROVAL_CANCELLED'
  | 'GITHUB_BRANCH_CREATED'
  | 'GITHUB_COMMIT_CREATED'
  | 'GITHUB_PR_CREATED'
  | 'GITHUB_MUTATION_FAILED'

export interface TaskResponse {
  id: string
  organizationId: string
  repositoryId: string
  createdByUserId: string
  sourceType: TaskSourceType
  sourceReference: string | null
  title: string
  description: string | null
  status: TaskStatus
  branchName: string | null
  currentStep: string | null
  failureCode: string | null
  failureMessage: string | null
  failedAt: string | null
  createdAt: string
  updatedAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface CreateTaskRequest {
  repositoryId: string
  title: string
  description?: string
}

export interface TaskEventResponse {
  id: string
  taskId: string
  organizationId: string
  eventType: TaskEventType
  previousState: TaskStatus | null
  newState: TaskStatus
  actorSource: TaskActorSource
  actorUserId: string | null
  occurredAt: string
  correlationId: string | null
}

// ==========================================
// Approvals & Sandbox
// ==========================================

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export type SandboxExecutionStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'CANCELLED'
  | 'RESOURCE_LIMIT'
  | 'EXECUTOR_UNAVAILABLE'

export interface ApprovalResponse {
  id: string
  proposedChangeId: string
  taskId: string
  repositoryId: string
  organizationId: string
  status: ApprovalStatus
  proposedChangeVersion: number
  sandboxExecutionId: string
  sandboxExecutionStatus: SandboxExecutionStatus
  requestedByUserId: string
  decidedByUserId: string | null
  decisionReason: string | null
  requestedAt: string
  decidedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface RequestApprovalRequest {
  proposedChangeId: string
}

export interface ApproveApprovalRequest {
  comment?: string
}

export interface RejectApprovalRequest {
  reason: string
}

// ==========================================
// GitHub Mutation
// ==========================================

export type GithubMutationStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'BRANCH_CREATED'
  | 'COMMITTED'
  | 'PR_CREATED'
  | 'SUCCEEDED'
  | 'FAILED'

export interface GithubMutationResponse {
  id: string
  organizationId: string
  repositoryId: string
  taskId: string
  proposedChangeId: string
  proposedChangeVersion: number
  approvalId: string
  sandboxExecutionId: string
  baseRevision: string | null
  branchName: string | null
  commitSha: string | null
  pullRequestNumber: number | null
  pullRequestUrl: string | null
  status: GithubMutationStatus
  failureCode: string | null
  failureMessage: string | null
  createdAt: string
  updatedAt: string
  version: number
}
