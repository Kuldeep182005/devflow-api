import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TaskEventTimeline } from './TaskEventTimeline'
import type { TaskEventResponse } from '@/types/api'

describe('TaskEventTimeline', () => {
  const mockEvents: TaskEventResponse[] = [
    {
      id: 'event-1',
      taskId: 'task-123',
      organizationId: 'org-123',
      eventType: 'TASK_CREATED',
      previousState: null,
      newState: 'QUEUED',
      actorSource: 'USER',
      actorUserId: 'user-1',
      occurredAt: '2026-09-30T10:00:00Z',
      correlationId: 'corr-task-created-11111111',
    },
    {
      id: 'event-2',
      taskId: 'task-123',
      organizationId: 'org-123',
      eventType: 'TASK_STARTED',
      previousState: 'QUEUED',
      newState: 'RUNNING',
      actorSource: 'WORKER',
      actorUserId: null,
      occurredAt: '2026-09-30T10:00:15Z',
      correlationId: 'corr-worker-started-22222222',
    },
    {
      id: 'event-3',
      taskId: 'task-123',
      organizationId: 'org-123',
      eventType: 'TASK_APPROVAL_REQUIRED',
      previousState: 'RUNNING',
      newState: 'WAITING_FOR_APPROVAL',
      actorSource: 'WORKER',
      actorUserId: null,
      occurredAt: '2026-09-30T10:02:30Z',
      correlationId: 'corr-approval-req-33333333',
    },
  ]

  it('renders empty state when no events exist', () => {
    render(<TaskEventTimeline events={[]} />)
    expect(screen.getByText('No events recorded yet')).toBeInTheDocument()
  })

  it('renders loading placeholder when isLoading is true', () => {
    const { container } = render(<TaskEventTimeline events={[]} isLoading={true} />)
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument()
  })

  it('renders list of chronological events with correct labels and actors', () => {
    render(<TaskEventTimeline events={mockEvents} />)

    expect(screen.getByText('Audit Event Timeline')).toBeInTheDocument()
    expect(screen.getByText('3 events')).toBeInTheDocument()

    // Event labels
    expect(screen.getByText('Task Created')).toBeInTheDocument()
    expect(screen.getByText('Agent Started')).toBeInTheDocument()
    expect(screen.getAllByText('Approval Required').length).toBe(2)

    // Actor badges
    expect(screen.getByText('USER')).toBeInTheDocument()
    const workerBadges = screen.getAllByText('WORKER')
    expect(workerBadges.length).toBe(2)

    // Correlation ID prefix
    expect(screen.getByText('id: corr-tas...')).toBeInTheDocument()
  })

  it('renders downstream PR and mutation events accurately', () => {
    const prEvents: TaskEventResponse[] = [
      {
        id: 'event-pr-1',
        taskId: 'task-123',
        organizationId: 'org-123',
        eventType: 'GITHUB_BRANCH_CREATED',
        previousState: 'RUNNING',
        newState: 'RUNNING',
        actorSource: 'SYSTEM',
        actorUserId: null,
        occurredAt: '2026-09-30T10:05:00Z',
        correlationId: 'corr-git-branch',
      },
      {
        id: 'event-pr-2',
        taskId: 'task-123',
        organizationId: 'org-123',
        eventType: 'GITHUB_PR_CREATED',
        previousState: 'RUNNING',
        newState: 'SUCCEEDED',
        actorSource: 'SYSTEM',
        actorUserId: null,
        occurredAt: '2026-09-30T10:06:00Z',
        correlationId: 'corr-git-pr',
      },
    ]

    render(<TaskEventTimeline events={prEvents} />)
    expect(screen.getByText('GitHub Branch Created')).toBeInTheDocument()
    expect(screen.getByText('GitHub PR Created')).toBeInTheDocument()
    const systemBadges = screen.getAllByText('SYSTEM')
    expect(systemBadges.length).toBe(2)
  })
})
