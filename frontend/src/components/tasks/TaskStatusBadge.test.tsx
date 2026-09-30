import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TaskStatusBadge } from './TaskStatusBadge'

describe('TaskStatusBadge', () => {
  it('renders QUEUED status correctly', () => {
    render(<TaskStatusBadge status="QUEUED" />)
    expect(screen.getByText('Queued')).toBeInTheDocument()
  })

  it('renders RUNNING status correctly', () => {
    render(<TaskStatusBadge status="RUNNING" />)
    expect(screen.getByText('Running')).toBeInTheDocument()
  })

  it('renders WAITING_FOR_APPROVAL status correctly', () => {
    render(<TaskStatusBadge status="WAITING_FOR_APPROVAL" />)
    expect(screen.getByText('Approval Required')).toBeInTheDocument()
  })

  it('renders SUCCEEDED status correctly', () => {
    render(<TaskStatusBadge status="SUCCEEDED" />)
    expect(screen.getByText('Succeeded')).toBeInTheDocument()
  })

  it('renders FAILED status correctly', () => {
    render(<TaskStatusBadge status="FAILED" />)
    expect(screen.getByText('Failed')).toBeInTheDocument()
  })

  it('renders CANCELLED status correctly', () => {
    render(<TaskStatusBadge status="CANCELLED" />)
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
  })

  it('hides icon when showIcon is false', () => {
    const { container } = render(<TaskStatusBadge status="QUEUED" showIcon={false} />)
    expect(container.querySelector('svg')).toBeNull()
    expect(screen.getByText('Queued')).toBeInTheDocument()
  })
})
