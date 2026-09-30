import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { GithubMutationStatusBadge } from './GithubMutationStatusBadge'

describe('GithubMutationStatusBadge', () => {
  it('renders QUEUED status badge correctly', () => {
    render(<GithubMutationStatusBadge status="QUEUED" />)
    expect(screen.getByText('Queued')).toBeInTheDocument()
  })

  it('renders RUNNING status badge correctly', () => {
    render(<GithubMutationStatusBadge status="RUNNING" />)
    expect(screen.getByText('Executing')).toBeInTheDocument()
  })

  it('renders BRANCH_CREATED status badge correctly', () => {
    render(<GithubMutationStatusBadge status="BRANCH_CREATED" />)
    expect(screen.getByText('Branch Created')).toBeInTheDocument()
  })

  it('renders COMMITTED status badge correctly', () => {
    render(<GithubMutationStatusBadge status="COMMITTED" />)
    expect(screen.getByText('Committed')).toBeInTheDocument()
  })

  it('renders PR_CREATED status badge correctly', () => {
    render(<GithubMutationStatusBadge status="PR_CREATED" />)
    expect(screen.getByText('PR Created')).toBeInTheDocument()
  })

  it('renders SUCCEEDED status badge correctly', () => {
    render(<GithubMutationStatusBadge status="SUCCEEDED" />)
    expect(screen.getByText('Succeeded')).toBeInTheDocument()
  })

  it('renders FAILED status badge correctly', () => {
    render(<GithubMutationStatusBadge status="FAILED" />)
    expect(screen.getByText('Failed')).toBeInTheDocument()
  })
})
