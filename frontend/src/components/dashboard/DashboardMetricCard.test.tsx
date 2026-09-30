import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { CheckSquare2 } from 'lucide-react'
import { DashboardMetricCard } from './DashboardMetricCard'

describe('DashboardMetricCard', () => {
  it('renders title, numeric value, description, and link', () => {
    render(
      <MemoryRouter>
        <DashboardMetricCard
          title="Active Tasks"
          value={5}
          description="In flight or formulating patch"
          icon={CheckSquare2}
          to="/organizations/org-1/tasks"
          variant="amber"
        />
      </MemoryRouter>
    )

    expect(screen.getByText('Active Tasks')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('In flight or formulating patch')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Active Tasks: 5/i })).toHaveAttribute(
      'href',
      '/organizations/org-1/tasks'
    )
  })

  it('renders loading state when isLoading is true', () => {
    render(
      <MemoryRouter>
        <DashboardMetricCard
          title="Awaiting Approval"
          value={null}
          description="Human authorization required"
          icon={CheckSquare2}
          isLoading={true}
        />
      </MemoryRouter>
    )

    expect(screen.getByText('Awaiting Approval')).toBeInTheDocument()
    expect(screen.queryByText('0')).toBeNull()
  })

  it('renders without link when to is not provided', () => {
    render(
      <DashboardMetricCard
        title="Completed Tasks"
        value={12}
        description="Successfully verified"
        icon={CheckSquare2}
      />
    )

    expect(screen.getByText('Completed Tasks')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.queryByRole('link')).toBeNull()
  })
})
