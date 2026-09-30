# DevFlow Platform — Frontend Console

Production-grade web console for the **DevFlow API** autonomous software engineering platform. Built with React 19, TypeScript, Vite, TanStack Query, and Tailwind CSS.

DevFlow provides controlled, auditable, and security-bounded AI software engineering workflows. The console connects directly to the authoritative Spring Boot backend API to guide engineering teams from task formulation through sandbox verification, human review, and pull request delivery.

---

## Engineering Workflow

The platform operationalizes a strict 11-stage software verification lifecycle:

```
[1. User Sign In]
       ↓
[2. Organization Selection]
       ↓
[3. Repository Connection (GitHub)]
       ↓
[4. Dispatch Engineering Task]
       ↓
[5. AI Worker Formulation]
       ↓
[6. Isolated Sandbox Verification]
       ↓
[7. Human Proposal Review (Diff & Sandbox Logs)]
       ↓
[8. Explicit Human Approval Gate]
       ↓
[9. Controlled GitHub Mutation]
       ↓
[10. Pull Request Created]
       ↓
[11. State Machine Audit & Event Trail]
```

1. **Authentication**: Sign in or register via Spring Security JWT endpoints (`/api/v1/auth/login`, `/api/v1/auth/register`).
2. **Organization Context**: Switch or manage workspaces with multi-tenant isolation.
3. **Repository Management**: Connect repositories backed by the organization's GitHub App installation.
4. **Task Dispatch**: Create autonomous software engineering tasks against target repositories.
5. **Worker Processing**: Background workers orchestrate code analysis and generate patch proposals.
6. **Sandbox Verification**: Patches are executed inside isolated Docker execution environments (`devflow-sandbox-executor`) to verify builds and test suites.
7. **Proposal Review**: Reviewers inspect unified git diffs, file modifications, and sandbox verification logs.
8. **Human Approval Gate**: Owner or Admin explicitly reviews and approves (or rejects with mandatory reason) the proposal.
9. **Controlled Remote Mutation**: The backend uses GitHub App installation tokens to create the remote branch and push the signed commit.
10. **Pull Request Delivery**: A pull request is created on GitHub referencing the audited task and proposal.
11. **Authoritative Event Trail**: Real-time state machine events (`TASK_*`, `APPROVAL_*`, `GITHUB_*`) record actors (`USER`, `WORKER`, `SYSTEM`) and state transitions.

---

## Security Invariants

- **Human Approval Security Boundary**: AI agents cannot push commits or create pull requests unilaterally. Every mutation requires explicit human authorization.
- **Isolated Execution**: Patch verification runs strictly within dedicated Docker sandbox containers, isolated from production hosts and credentials.
- **No Client-Side Secrets**: GitHub App private keys, installation tokens, and database credentials are held exclusively by the Spring Boot backend. The frontend never receives nor handles GitHub secrets.
- **Authoritative Data Only**: All dashboard metrics, status badges, timelines, and diffs are derived directly from authoritative backend API responses. No synthetic metrics, fake accuracy scores, or fabricated activity.
- **Auditable State Transitions**: All state changes are driven by the PostgreSQL-backed task state machine with recorded timestamps, actor sources, and correlation IDs.

---

## Tech Stack

- **Runtime & Build**: React 19, TypeScript 5, Vite 8
- **Routing**: React Router 7 (declarative route guards, nested layouts, organization-scoped routing)
- **Data Fetching & Caching**: TanStack Query 5 (bounded polling, cache invalidation, deduplication)
- **Styling**: Tailwind CSS 4, Lucide React icons
- **Testing**: Vitest, React Testing Library, jsdom
- **Linting**: Oxlint

---

## Getting Started

### Prerequisites

- Node.js 20+
- Running DevFlow API backend (`http://localhost:8080`)

### Installation

```bash
npm install
```

### Development Server

```bash
npm run dev
```

The application starts on `http://localhost:5173`. API requests to `/api` are proxied to the backend at `http://localhost:8080`.

### Running Verification Tests

```bash
npm test
```

### Running Linter

```bash
npm run lint
```

### Production Build

```bash
npm run build
```

---

## Project Structure

```
src/
├── api/                  # Centralized HTTP client & endpoint modules
│   ├── approvals.ts      # Human approval and proposal review API
│   ├── auth.ts           # Authentication & registration endpoints
│   ├── client.ts         # Axios/Fetch wrapper with JWT injection & error handling
│   ├── github.ts         # Post-approval GitHub mutation execution & status
│   ├── health.ts         # Backend connectivity & health checks
│   ├── organizations.ts  # Workspace & membership management
│   ├── repositories.ts   # Repository configuration & GitHub connection
│   └── tasks.ts          # Task lifecycle & state machine events
├── components/           # Reusable UI component library
│   ├── approvals/        # Diff viewer, approval cards, decision modals
│   ├── common/           # Spinners, badges, error states, empty states
│   ├── dashboard/        # Authoritative metric cards & recent activity feed
│   ├── github/           # Mutation status cards, PR links, execution dialogs
│   ├── organizations/    # Organization switcher, creation modals, member tables
│   ├── repositories/     # Repository tables, GitHub connect modals
│   ├── tasks/            # Task creation, lifecycle steppers, event timelines
│   └── ui/               # Core button, card, badge, dialog primitives
├── features/             # Business domain contexts & route guards
│   ├── auth/             # Session persistence & protected route handling
│   └── organizations/    # Active organization state & preferences
├── hooks/                # Custom React hooks (useAuth, useActiveOrganization)
├── layouts/              # Shell layout with responsive navigation & health indicator
├── lib/                  # Utilities, date formatters, URL sanitization
├── pages/                # Route-level view components
└── types/                # Authoritative TypeScript DTO interfaces matching backend
```
