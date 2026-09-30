import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { RecentActivity, type EnrichedActivityEvent } from './RecentActivity'

describe('RecentActivity', () => {
  const mockEvents: EnrichedActivityEvent[] = [
    {
      id: 'event-1',
      taskId: 'task-100',
      taskTitle: 'Refactor JWT authentication filter',
      eventType: 'GITHUB_PR_CREATED',
      occurredAt: '2026-09-30T12:00:00Z',
      actorSource: 'WORKER',
      newState: 'SUCCEEDED',
    },
    {
      id: 'event-2',
      taskId: 'task-200',
      taskTitle: 'Add rate limiting to public endpoints',
      eventType: 'TASK_APPROVAL_REQUIRED',
      occurredAt: '2026-09-30T11:45:00Z',
      actorSource: 'SYSTEM',
      newState: 'WAITING_FOR_APPROVAL',
    },
    {
      id: 'event-3',
      taskId: 'task-300',
      taskTitle: 'Fix database connection timeout',
      eventType: 'APPROVAL_APPROVED',
      occurredAt: '2026-09-30T11:30:00Z',
      actorSource: 'USER',
      newState: 'RUNNING',
    },
  ]

  it('renders list of real events with labels, task titles, actors, and links', () => {
    render(
      <MemoryRouter>
        <RecentActivity
          events={mockEvents}
          organizationId="org-123"
        />
      </MemoryRouter>
    )

    expect(screen.getByText('Recent Engineering Activity')).toBeInTheDocument()
    expect(screen.getByText('3 events')).toBeInTheDocument()

    expect(screen.getByText('Pull Request Opened')).toBeInTheDocument()
    expect(screen.getByText('Refactor JWT authentication filter')).toBeInTheDocument()
    expect(screen.getByText('Human Review Gate Required')).toBeInTheDocument()
    expect(screen.getByText('Add rate limiting to public endpoints')).toBeInTheDocument()
    expect(screen.getByText('Proposal Approved')).toBeInTheDocument()
    expect(screen.getByText('Fix database connection timeout')).toBeInTheDocument()

    // Links to task detail
    const prLink = screen.getByRole('link', { name: /Pull Request Opened/i })
    expect(prLink).toHaveAttribute('href', '/organizations/org-123/tasks/task-100')
  })

  it('renders loading state when isLoading is true', () => {
    render(
      <MemoryRouter>
        <RecentActivity
          events={[]}
          organizationId="org-123"
          isLoading={true}
        />
      </MemoryRouter>
    )

    expect(screen.getByText('Loading recent events...')).toBeInTheDocument()
  })

  it('renders empty state when there are no events', () => {
    render(
      <MemoryRouter>
        <RecentActivity
          events={[]}
          organizationId="org-123"
          isLoading={false}
        />
      </MemoryRouter>
    )

    expect(screen.getByText('No recent activity')).toBeInTheDocument()
    expect(screen.getByText(/Autonomous agent proposals, sandbox runs, and GitHub deliveries/i)).toBeInTheDocument()
  })
})
