# DevFlow API

DevFlow is a secure AI developer platform API providing multi-tenant repository orchestration, verified proposed changes, and isolated sandbox execution.

## Sandbox Security Model

DevFlow enforces a strict isolation boundary between untrusted repository code, the host API application, and the container execution engine:

```
DevFlow API
    |
    | authenticated sandbox protocol (X-Sandbox-Token)
    v
Sandbox Executor
    |
    | Docker daemon access (privileged boundary)
    v
Isolated Container
```

**"The DevFlow API does not have Docker daemon access."**

- **Privileged Daemon Boundary Separation**: Direct Docker daemon control (socket / named pipe) is strictly isolated within the standalone `devflow-sandbox-executor` daemon process. The `devflow-api` process does not import Docker Java libraries, does not have socket/named-pipe access, and can only communicate with the executor through a narrowly scoped, authenticated HTTP protocol (`X-Sandbox-Token`).
- **Controlled Execution Protocol**: The API can only request an execution ID, proposed change ID, allowlisted execution profile (`JAVA_MAVEN_TEST`), and controlled execution files payload. Callers, LLMs, and API clients cannot supply arbitrary Docker commands, base images, volumes, network configurations, or privileged flags.
- **Repository Code is Untrusted**: All repository code, build scripts (e.g. Maven plugins), test code, dependencies, and process outputs are treated as hostile and untrusted.
- **Execution Outside API JVM**: Repository code is NEVER executed directly on the DevFlow API host or inside the JVM (no `ProcessBuilder`, `Runtime.exec`, or host shell processes). Execution is strictly delegated to isolated container sandboxes.
- **No Host Filesystem Mount**: The developer's host filesystem, user home directories, project root, and host credentials (`.ssh`, `.aws`, `.config`, tokens, environment secrets) are NEVER mounted into the execution sandbox. Only a dedicated, randomly generated temporary execution directory under a configured workspace root is mounted.
- **No Docker Socket in Sandbox**: The Docker daemon socket (`/var/run/docker.sock` or Windows named pipe) is NEVER mounted or exposed to the container. Sandboxed workloads cannot control Docker or escape to the host engine.
- **No Privileged Mode & Dropped Capabilities**: Containers run with `privileged=false`, all Linux capabilities dropped (`cap_drop=ALL`), and `no-new-privileges=true`. The container root filesystem is mounted read-only (`readonly_rootfs=true`).
- **No Unrestricted Network**: Default network policy is completely disabled (`network_mode: none`). Sandboxed processes cannot access the public internet, host services, PostgreSQL, Redis, GitHub, or internal cloud metadata endpoints.
- **Explicit Resource Limits**: Enforces strict, server-controlled resource boundaries at startup: CPU periods and quotas, memory limits (default 1GB), process limits (default 256 PIDs), maximum file counts, maximum file sizes, and workspace byte limits.
- **Bounded Output Sanitization**: Process stdout and stderr are treated as untrusted, captured incrementally with strict character bounds (`maxOutputCharacters=20000`), and automatically truncated to prevent denial-of-service via log explosion or database overflow. Full output is never blindly logged.
- **Strict Timeout and Cancellation**: Sandboxed executions are bounded by hard timeouts (default 2 minutes) and server-controlled cancellation. When a deadline is exceeded or cancellation is requested, containers and associated workspaces are forcefully terminated and purged.
- **Server-Defined Execution Profiles**: Execution commands are strictly server-defined through strongly typed profiles (e.g., `JAVA_MAVEN_TEST`). Neither LLMs nor HTTP clients can supply arbitrary commands, base images, mounts, or environment variables.
- **Host Secret Isolation**: Container environments are explicitly constructed with safe baseline variables (`CI=true`, `LANG=C.UTF-8`). No host environment variables, database credentials, Redis passwords, or API keys are passed.
- **Proposal is NOT Approval**: Executing a sandboxed test on a persisted `ProposedChange` does not imply human or organizational approval. Approval boundaries are strictly enforced by the Batch 15 Human Approval system.
- **No GitHub Mutations**: Sandboxed execution and human approvals never create branches, commits, pushes, or pull requests on GitHub. GitHub mutations remain strictly isolated for future Batch 16.

## Human Approval Security Boundary

The approval system is a strict security boundary preventing unauthorized or unverified AI mutations from reaching repositories.

```
Task
    ↓
Agent
    ↓
ProposedChange
    ↓
Sandbox Execution
    ↓
Human Approval          ← BATCH 15 SECURITY BOUNDARY
    ↓
GitHub Mutation         ← BATCH 16 CONTROLLED MUTATION PIPELINE
    ↓
Deterministic Branch
    ↓
Atomic Commit
    ↓
Pull Request
```

**"Sandbox success is not human approval."**

**"Approval is tied to the exact ProposedChange version and successful sandbox execution."**

### Approval Lifecycle & Invariants

1. **AI creates ProposedChange**: An agent generates a patch targeting a specific repository task.
2. **ProposedChange is validated**: Syntax, paths, and patch boundaries are strictly validated and the proposal transitions to `VALIDATED`.
3. **Sandbox executes exact proposal version**: An isolated sandbox execution runs against the exact proposal version (`proposedChangeVersion`).
4. **Human reviews proposal and sandbox result**: An authenticated human reviews the code diff, task context, and successful execution output.
5. **Authorized human explicitly approves/rejects**:
   - Only explicitly authorized organization roles (`OWNER` and `ADMIN`) can record a decision. `MEMBER` and `VIEWER` roles are rejected with 403 Forbidden.
   - Approval cannot be granted without a completed, non-stale `SUCCEEDED` sandbox execution.
   - If the `ProposedChange` evolves (version bump), any existing approval target or previous sandbox execution becomes stale and approval is rejected with 409 Conflict.
6. **Approval is immutable and auditable**:
   - Every approval decision is permanently recorded in the `approvals` ledger with optimistic concurrency control (`@Version`).
   - Terminal states (`APPROVED`, `REJECTED`, `CANCELLED`) cannot be transitioned or contradicted.
   - Every approval transition atomically persists a corresponding `TaskEvent` (`APPROVAL_REQUESTED`, `APPROVAL_APPROVED`, `APPROVAL_REJECTED`, `APPROVAL_CANCELLED`) with decider and approval ID metadata. If event insertion fails, the decision transaction rolls back.
   - Complete tenant isolation is enforced: Cross-organization lookups return 404 without leaking existence (IDOR prevention). Database composite foreign keys enforce relational integrity across `(organization_id, proposed_change_id)`, `(organization_id, task_id)`, and `(organization_id, repository_id)`.
7. **Only approved proposals can initiate GitHub mutations**:
   - Mutation requests can only be initiated by authorized organization members (`OWNER` or `ADMIN`) against valid `APPROVED` records.

---

## Controlled GitHub Mutation Pipeline (Batch 16)

DevFlow mutates GitHub strictly through a controlled, deterministic, auditable, and idempotent pipeline.

```
POST /api/v1/organizations/{orgId}/approvals/{approvalId}/execute
    |
    v
Validate Tenant, Role (OWNER/ADMIN), Approval (APPROVED), Exact Version, Sandbox (SUCCEEDED)
    |
    v
Create / Find Durable GithubMutation (Idempotent Identity: org + repo + change + version)
    |
    v
Enqueue GITHUB_PR_CREATION to Redis Outbox → Return 202 Accepted
    |
    v
[Async Worker: GithubMutationExecutor]
    |
    |-- 1. Resolve Remote Base Revision -> Verify Matches Expected base_revision (No stale mutation / no rebase)
    |-- 2. Pure Java Patch Application (ADD, MODIFY, DELETE) against base files fetched via Git Data API
    |-- 3. Create Blobs + Git Tree + Git Commit (Atomic 1-commit change)
    |-- 4. Create Deterministic Branch ref (devflow/task-{taskId}/change-{proposalId})
    |-- 5. Create Pull Request targeting base branch
    |-- 6. Persist Audit Events (GITHUB_BRANCH_CREATED, GITHUB_COMMIT_CREATED, GITHUB_PR_CREATED)
```

### Core Security & Architectural Invariants

1. **DevFlow Never Executes Repository Code on the API Host**:
   - No `git` CLI, `ProcessBuilder`, `Runtime.exec`, or shell invocations.
   - Git operations are performed exclusively via GitHub Git Data REST APIs over HTTPS.
   - Patch application is performed using pure Java diff parsing and application in memory with strict path and size constraints.
2. **GitHub Mutations Require Explicit Human Approval**:
   - `NO APPROVAL = NO GITHUB MUTATION`.
   - The approval record must be in status `APPROVED`.
3. **Only Exact Approved Proposal Version Can Be Executed**:
   - `APPROVED VERSION != PROPOSED CHANGE VERSION = REJECTION`.
   - If a proposal is edited or versioned, any prior approval is invalid for execution.
4. **Successful Sandbox Validation Required**:
   - A sandbox execution for the exact `(proposedChangeId, proposedChangeVersion)` must exist in status `SUCCEEDED`.
5. **Stale Base Revision Protection**:
   - The GitHub remote base branch revision is resolved before mutation. If it does not match `proposedChange.getBaseRevision()`, the pipeline **STOPS** and fails with `BASE_REVISION_STALE`.
   - DevFlow **NEVER** automatically rebases, regenerates patches, or silently commits against newer remote code.
6. **Deterministic Server-Generated Branch Names**:
   - Clients and LLMs cannot provide branch names, commit SHAs, or PR targets.
   - Branches follow the format: `devflow/task-{taskId}/change-{proposalId}`.
   - If the branch already exists, DevFlow verifies whether it belongs to this mutation (matching commit SHA); it never overwrites unrelated branches or force-pushes.
7. **Single Atomic Commit via Git Data APIs**:
   - All modified/added/deleted files in the proposal are combined into a single Git Tree and Commit.
   - Avoids intermediate or multi-commit states.
8. **No Automatic Merging**:
   - DevFlow only creates the pull request. It never merges pull requests, creates releases/tags, or alters repository settings or branch protection rules.
9. **Idempotency & Safe Reconciliation**:
   - Uniqueness constraint `uk_github_mutation_identity` on `(organization_id, repository_id, proposed_change_id, proposed_change_version)` prevents duplicate records.
   - Re-requesting execution returns the existing `GithubMutation` with HTTP 202.
   - Workers reconcile remote state prior to creating resources (checking if branch, commit, or PR already exist on GitHub), preventing duplicate mutations across retries, network timeouts, or worker restarts.
10. **Transaction Boundaries & External Systems**:
    - GitHub is an external system; local database transactions cannot atomically commit GitHub operations.
    - Database transactions are never held open across external GitHub HTTP calls. Each phase (status claim, branch creation, commit creation, PR creation, completion) commits in its own local transaction.
11. **Server-Side Credential Isolation**:
    - GitHub App Private Keys and installation tokens remain strictly server-side.
    - Tokens are never exposed in API responses, logs, worker payloads, or sandbox containers.

### Local Development Setup

To configure GitHub App integration for local development, supply the following properties in `application.yml` or environment variables:

```yaml
devflow:
  github:
    app-id: "123456"
    private-key-pem: |
      -----BEGIN RSA PRIVATE KEY-----
      ... (PKCS#8 or PKCS#1 RSA Private Key) ...
      -----END RSA PRIVATE KEY-----
    api-base-url: "https://api.github.com" # Or custom mock server URL for testing
    connect-timeout-seconds: 10
    read-timeout-seconds: 30
```

> **Note**: For integration and unit tests, DevFlow uses an in-process mock HTTP server or WireMock, completely eliminating the need for real GitHub credentials during CI/CD.

---

## Observability, Evaluation & Production Hardening (Batch 17)

Batch 17 makes DevFlow observable, diagnosable, measurable, resilient, and secure under load without adding major new product features or unnecessary infrastructure.

### 1. Request & Correlation ID (`X-Request-Id`)

Every HTTP request passing through DevFlow receives an unambiguous correlation identifier:
- **Header**: `X-Request-Id`
- **Validation**: Incoming headers must match `^[a-zA-Z0-9_-]{1,64}$`. Oversized (>64 chars) or malformed IDs containing invalid characters are discarded and replaced with a cryptographically strong UUID (`UUID.randomUUID().toString()`).
- **MDC Context**: Injected into SLF4J MDC (`requestId` and `correlationId`) by `RequestIdFilter`.
- **Thread Safety**: MDC is guaranteed to be cleared in a `finally` block upon completion of the servlet filter chain, preventing MDC leakage across thread pools.
- **Propagation**: Propagated through response headers and included in uniform API error envelopes.

### 2. Structured Logging & Secret Redaction

Logging across the platform uses structured key-value context for machine parsing:
- **Safe Fields**: `requestId`, `correlationId`, `taskId`, `organizationId`, `repositoryId`, `approvalId`, `proposedChangeId`, `mutationId`, `jobId`, `operation`, `outcome`, `durationMs`.
- **Absolute Redaction**: DevFlow strictly forbids logging:
  - Passwords, database credentials, and Redis secrets.
  - JWT access tokens and Bearer authentication headers.
  - GitHub App private keys, installation tokens, and webhook secrets.
  - Full LLM prompts and responses (which could contain sensitive repository source code).
  - Unbounded sandbox stdout/stderr or internal stack traces to clients.

### 3. Spring Boot Actuator & Health Probes

Actuator is strictly hardened to expose only operational probes:
- **Endpoints Exposed**:
  - `GET /actuator/health`: Aggregated health.
  - `GET /actuator/health/readiness`: Kubernetes/Docker readiness probe verifying database connectivity (`db`) and application state.
  - `GET /actuator/health/liveness`: Kubernetes/Docker liveness probe (`livenessState`).
  - `GET /actuator/info` and `GET /actuator/metrics`: Operational monitoring.
- **Endpoints Blocked**: Unrestricted management endpoints (`env`, `beans`, `configprops`, `heapdump`, `threaddump`, `mappings`, `loggers`) are completely disabled and unexposed.
- **Security**: Actuator health and probe endpoints are publicly accessible for container orchestrators, while all other actuator endpoints require authenticated access.

### 4. Application Metrics (Micrometer)

Application metrics are recorded in `DevFlowMetrics` using Prometheus/Micrometer standard conventions.
**Low-Cardinality Invariant**: High-cardinality identifiers (`taskId`, `userId`, `organizationId`, `repositoryId`, `requestId`, `mutationId`) are **strictly prohibited** from metric dimensions.

Metric families:
- **HTTP**: `http.server.requests` (count, latency, status code, method).
- **Tasks**: `devflow.tasks.created`, `devflow.tasks.transitions` (`fromStatus`, `toStatus`), `devflow.tasks.failures` (`reason`), `devflow.tasks.execution.time`.
- **Workers**: `devflow.workers.jobs.received`, `devflow.workers.jobs.succeeded`, `devflow.workers.jobs.failed` (`jobType`, `reason`), `devflow.workers.jobs.retried`, `devflow.workers.processing.time`, `devflow.workers.stale.recovered`.
- **LLM**: `devflow.llm.requests` (`provider`, `status`), `devflow.llm.failures` (`provider`, `reason`), `devflow.llm.request.duration`, `devflow.llm.tokens.prompt`, `devflow.llm.tokens.completion`.
- **Repository Tools**: `devflow.tools.calls` (`tool`, `status`), `devflow.tools.execution.time`, `devflow.tools.failures`.
- **Sandbox**: `devflow.sandbox.executions` (`profile`, `status`), `devflow.sandbox.failures` (`reason`), `devflow.sandbox.execution.time`.
- **Approvals**: `devflow.approvals.requested`, `devflow.approvals.decided` (`decision`), `devflow.approvals.stale`.
- **GitHub Mutations**: `devflow.github.mutations.attempts`, `devflow.github.mutations.success`, `devflow.github.mutations.failures` (`stage`, `reason`), `devflow.github.mutations.duration`.
- **Security**: `devflow.security.auth.failures` (`reason`), `devflow.security.authz.failures` (`reason`), `devflow.security.webhook.failures` (`reason`), `devflow.security.ratelimit.exceeded` (`tier`).
- **AI Evaluation**: `devflow.eval.agent.success`, `devflow.eval.agent.failure` (`failureCode`), `devflow.eval.iterations`, `devflow.eval.tool_calls`.

### 5. Tiered Rate Limiting

DevFlow protects high-risk endpoints against abuse and brute-force via `RateLimitFilter` and `RedisRateLimiter`:
- **Auth Login**: 5 requests / min (IP-based).
- **Auth Register**: 3 requests / min (IP-based).
- **Webhooks**: 60 requests / min (IP-based).
- **Task Creation**: 30 requests / min (Principal / IP).
- **Approval Execution**: 10 requests / min (Principal / IP).
- **Global API**: 300 requests / min (Principal / IP).
- **Fail-Safe In-Memory Fallback**: If Redis is temporarily unreachable, the rate limiter automatically falls back to an internal bounded LRU cache (max 5,000 keys) without disrupting synchronous APIs or throwing 500 errors.
- **HTTP 429 Response**: Rejections emit HTTP 429 Too Many Requests with a `Retry-After: <seconds>` header and a uniform `ApiError` JSON body.

### 6. Uniform Global Error Handling (`ApiError`)

All exceptions are intercepted by `GlobalExceptionHandler` and mapped to a standard schema:
```json
{
  "timestamp": "2026-09-30T00:15:00Z",
  "requestId": "a3b1c9d2-...",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Resource not found",
  "path": "/api/v1/organizations/.../tasks/..."
}
```
- **Information Leakage Prevention**: Stack traces, SQL queries, database schema details, filesystem paths, and external raw responses are stripped.
- **Tenant Isolation**: Cross-organization resource queries return 404 Not Found rather than 403 Forbidden to prevent leaking the existence of resources in other tenants.

### 7. AI Evaluation Ledger & Safety Metrics

A dedicated, lightweight evaluation model (`ai_evaluations` table, Flyway V13) records the lifecycle of AI agent runs:
- **Columns**: `task_id`, `organization_id`, `provider`, `model`, `agent_execution_id`, `started_at`, `completed_at`, `iteration_count`, `tool_call_count`, `final_state`, `failure_code`, `proposed_change_id`, `sandbox_execution_id`, `approval_id`, `github_mutation_id`.
- **Integrity**: Enforces relational composite foreign keys against `tasks`, `proposed_changes`, `sandbox_executions`, `approvals`, and `github_mutations`.
- **Auditability**: Connects the end-to-end lineage: `Task` → `Agent Iterations` → `Proposal` → `Sandbox Test` → `Human Approval` → `GitHub PR`, allowing operational answers to model accuracy, failure categories, and sandbox validation rates without storing raw prompts or source code diffs in evaluation records.

### 8. Database & Infrastructure Hardening

- **Hikari Connection Pool**: Configured with `maximum-pool-size: 10`, `minimum-idle: 5`, `connection-timeout: 20000ms`, `idle-timeout: 300000ms`, `max-lifetime: 1200000ms` for predictable resource usage.
- **Database Indexing**: Added composite index `idx_tasks_org_status_created` on `tasks(organization_id, status, created_at DESC)` for efficient tenant task queue filtering.
- **HTTP Security Headers**: Enforces `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store, must-revalidate`, and `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
- **Bounded Timeouts**: All external network calls (GitHub API, LLM provider, Sandbox Executor) have enforced connect and read timeouts (10s connect, 30s read).

---

## CI/CD, Automated Gates & Production Containerization (Batch 18)

DevFlow uses automated quality/security gates, reproducible builds, and container isolation across local development and GitHub Actions pipelines.

### 1. CI/CD Architecture

```
Developer
   ↓
GitHub PR / Push
   ↓
GitHub Actions (Least-Privilege: contents: read)
   ├── Java 21 Setup + Maven Wrapper Caching
   ├── Maven Reactor Verify (mvn clean verify)
   │     ├── Unit Tests
   │     ├── Web MVC / Security Tests
   │     ├── PostgreSQL Testcontainers Integration Tests
   │     ├── Redis Testcontainers Integration Tests
   │     └── Isolated Sandbox Executor Tests
   ├── Dependency Security Scan (OWASP Dependency-Check, CVSS >= 9)
   ├── Secret Scanning (TruffleHog Verified Secrets)
   ├── Docker Multi-Stage Image Builds (OCI Provenance Labels)
   ├── Image Smoke Tests (Non-Root User, Docker Socket Absence in API)
   └── Container Vulnerability Scan (Trivy CRITICAL/HIGH Gate)
           ↓
      Verified Image
           ↓
        GHCR (Release Workflow / Trusted Tags Only)
           ↓
      Deployment Readiness
```

> **IMPORTANT**: Automatic production deployment is **NOT** enabled. Images are built, verified, and published to GHCR upon verified release tags. Actual deployment to cloud infrastructure requires explicit deployment targets and infrastructure configuration.

---

### 2. GitHub Actions Pipelines

| Workflow | File | Triggers | Permissions | Key Actions |
| --- | --- | --- | --- | --- |
| **CI** | `.github/workflows/ci.yml` | Pull Request, Push to `main`/`master` | `contents: read` | Maven verify, Testcontainers, OWASP scan, TruffleHog, Docker builds, smoke tests, Trivy |
| **Release** | `.github/workflows/release.yml` | Git tags (`v*.*.*`), Workflow dispatch | `contents: read`, `packages: write` | Full verify, immutable tag generation (`<sha>`, `<version>`), Trivy gate, GHCR push |

#### Security & Least-Privilege Design:
- **No `write-all` permissions**: CI uses strictly scoped `contents: read`. Release only adds `packages: write` for GHCR publication.
- **Fork PR Isolation**: Pull requests from untrusted forks do not have access to repository secrets, cannot publish to GHCR, and cannot perform mutations.
- **No Shell Injection**: Workflow shell steps use static commands and safe environment variable passing instead of interpolating `${{ github.event.* }}` inside script bodies.
- **No Bypassing Gates**: No `continue-on-error: true` or `|| true` on mandatory test, vulnerability, or container security gates. Failures block the pipeline.
- **Deterministic Tagging**: Images are tagged immutably with the Git commit SHA (`ghcr.io/org/devflow-api:a1b2c3d`) and release semantic version. `latest` is never used as the sole identifier.

---

### 3. Container Security Architecture

DevFlow maintains a strict security boundary between `devflow-api` and `devflow-sandbox-executor`:

| Image | Base Image | User | Docker Socket Access | OCI Labels | Purpose |
| --- | --- | --- | --- | --- | --- |
| `devflow-api` | `eclipse-temurin:21-jre-alpine` | Non-root (`10001:devflow`) | **NONE** (`/var/run/docker.sock` is NOT mounted) | Yes | Core API, Auth, Task state, LLM orchestration |
| `devflow-sandbox-executor` | `eclipse-temurin:21-jre-alpine` | Non-root (`10002:devflow`) | **RUNTIME ONLY** (Mounted explicitly to execute isolated tasks) | Yes | Isolated container execution daemon |

- **No Root Execution**: Both runtime containers run under dedicated non-root UIDs (`10001` and `10002`).
- **No Source Code in Runtime**: Multi-stage builds compile via Maven wrapper in stage 1 (`eclipse-temurin:21-jdk-alpine`) and copy only the final runnable JAR into the minimal runtime stage (`eclipse-temurin:21-jre-alpine`).
- **Graceful Shutdown**: JVM `ENTRYPOINT` uses exec array syntax (`["java", ...]`), ensuring `SIGTERM` signals from Docker or Kubernetes are received directly by the Spring Boot application.
- **Container-Aware JVM**: Configured with `-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage=75.0`, and `-XX:+ExitOnOutOfMemoryError`.
- **Health & Probes**: Both containers define `HEALTHCHECK` instructions:
  - `devflow-api`: `/actuator/health/liveness`
  - `devflow-sandbox-executor`: `/actuator/health`

---

### 4. Local Developer Commands

#### Prerequisites
- JDK 21 (Eclipse Temurin recommended)
- Node.js 20+ (for web console)
- Docker & Docker Compose v2+
- Git

#### Running the Backend Test Suite
```bash
# Run all unit tests, MVC tests, Testcontainers, and executor tests:
./mvnw clean verify

# On Windows:
mvnw.cmd clean verify
```

#### Running the Frontend Web Console
```bash
# Navigate to frontend directory:
cd frontend

# Install dependencies:
npm install

# Run unit and component test suite (186 tests):
npm test -- --run

# Run linter:
npm run lint

# Build production bundle:
npm run build

# Start local development server (proxies to http://localhost:8080):
npm run dev
```

#### Running Dependency Vulnerability Scans Locally
```bash
# Run OWASP Dependency-Check across all reactor modules:
./mvnw org.owasp:dependency-check-maven:aggregate -DfailBuildOnCVSS=9

# View report:
# target/dependency-check-report.html
```

#### Running with Docker Compose
```bash
# 1. Initialize environment file from safe template:
cp .env.example .env
# Edit .env with your local secrets

# 2. Build and start all services (PostgreSQL, Redis, API, Sandbox Executor):
docker compose up --build

# 3. Validate docker-compose configuration without starting:
docker compose --env-file .env config

# 4. Stop and clean up containers:
docker compose down
```

#### Building Docker Images Manually
```bash
# Build API image (from repository root):
docker build -f devflow-api/Dockerfile -t devflow-api:local .

# Build Sandbox Executor image (from repository root):
docker build -f devflow-sandbox-executor/Dockerfile -t devflow-sandbox-executor:local .
```

---

### 5. Recommended Branch Protection Policies

To guarantee the integrity of protected branches (`main`, `master`), configure the following settings in GitHub repository settings:
- **Require a pull request before merging**: Prevent direct pushes to default branch.
- **Require status checks to pass before merging**:
  - `Build & Test (Java 21)` (Maven Verify with Testcontainers)
  - `Dependency Security Scan` (OWASP Dependency-Check)
  - `Secret Scan` (TruffleHog)
  - `Docker Build & Verify` (Docker build, smoke tests, Trivy)
- **Require branches to be up to date before merging**: Ensure tests ran against latest base.
- **Require approvals**: At least 1 peer review required before merge.
- **Do not allow bypassing the above settings**: Even administrators and the DevFlow GitHub App cannot bypass required gates.
- **Block force pushes & branch deletion**: Protect git history provenance.
