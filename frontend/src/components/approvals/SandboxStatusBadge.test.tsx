import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SandboxStatusBadge } from './SandboxStatusBadge'

describe('SandboxStatusBadge', () => {
  it('renders SUCCEEDED status badge correctly', () => {
    render(<SandboxStatusBadge status="SUCCEEDED" />)
    expect(screen.getByText('Sandbox Passed')).toBeInTheDocument()
  })

  it('renders RUNNING status badge correctly', () => {
    render(<SandboxStatusBadge status="RUNNING" />)
    expect(screen.getByText('Sandbox Running')).toBeInTheDocument()
  })

  it('renders QUEUED status badge correctly', () => {
    render(<SandboxStatusBadge status="QUEUED" />)
    expect(screen.getByText('Sandbox Queued')).toBeInTheDocument()
  })

  it('renders FAILED status badge correctly', () => {
    render(<SandboxStatusBadge status="FAILED" />)
    expect(screen.getByText('Sandbox Failed')).toBeInTheDocument()
  })

  it('renders TIMED_OUT status badge correctly', () => {
    render(<SandboxStatusBadge status="TIMED_OUT" />)
    expect(screen.getByText('Sandbox Timed Out')).toBeInTheDocument()
  })

  it('renders RESOURCE_LIMIT status badge correctly', () => {
    render(<SandboxStatusBadge status="RESOURCE_LIMIT" />)
    expect(screen.getByText('Resource Limit Exceeded')).toBeInTheDocument()
  })

  it('renders EXECUTOR_UNAVAILABLE status badge correctly', () => {
    render(<SandboxStatusBadge status="EXECUTOR_UNAVAILABLE" />)
    expect(screen.getByText('Executor Unavailable')).toBeInTheDocument()
  })

  it('renders CANCELLED status badge correctly', () => {
    render(<SandboxStatusBadge status="CANCELLED" />)
    expect(screen.getByText('Sandbox Cancelled')).toBeInTheDocument()
  })
})
