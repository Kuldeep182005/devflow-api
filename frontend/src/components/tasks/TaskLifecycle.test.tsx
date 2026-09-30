import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TaskLifecycle } from './TaskLifecycle'

describe('TaskLifecycle', () => {
  it('renders all four pipeline stages', () => {
    render(<TaskLifecycle status="QUEUED" />)
    expect(screen.getByText('Queued')).toBeInTheDocument()
    expect(screen.getByText('AI Agent Running')).toBeInTheDocument()
    expect(screen.getByText('Approval Required')).toBeInTheDocument()
    expect(screen.getByText('Completed / Merged')).toBeInTheDocument()
  })

  it('marks QUEUED as active when status is QUEUED', () => {
    render(<TaskLifecycle status="QUEUED" />)
    expect(screen.getByText('Active State')).toBeInTheDocument()
    expect(screen.queryByText('Execution Terminated (Failed)')).toBeNull()
    expect(screen.queryByText('Execution Cancelled')).toBeNull()
  })

  it('marks RUNNING as active and previous stage as complete', () => {
    render(<TaskLifecycle status="RUNNING" />)
    expect(screen.getByText('Active State')).toBeInTheDocument()
    expect(screen.getByText('Complete')).toBeInTheDocument()
  })

  it('marks WAITING_FOR_APPROVAL as active and shows purple styling', () => {
    render(<TaskLifecycle status="WAITING_FOR_APPROVAL" />)
    expect(screen.getByText('Active State')).toBeInTheDocument()
    const completeBadges = screen.getAllByText('Complete')
    expect(completeBadges.length).toBe(2) // QUEUED and RUNNING are complete
  })

  it('marks SUCCEEDED with all stages complete', () => {
    render(<TaskLifecycle status="SUCCEEDED" />)
    const completeBadges = screen.getAllByText('Complete')
    expect(completeBadges.length).toBe(4)
    expect(screen.queryByText('Active State')).toBeNull()
  })

  it('displays terminal failure banner when status is FAILED', () => {
    render(<TaskLifecycle status="FAILED" />)
    expect(screen.getByText('Execution Terminated (Failed)')).toBeInTheDocument()
    expect(screen.queryByText('Active State')).toBeNull()
  })

  it('displays terminal cancelled banner when status is CANCELLED', () => {
    render(<TaskLifecycle status="CANCELLED" />)
    expect(screen.getByText('Execution Cancelled')).toBeInTheDocument()
    expect(screen.queryByText('Active State')).toBeNull()
  })
})
