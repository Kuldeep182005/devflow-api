import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GithubMutationCard } from './GithubMutationCard'
import type { GithubMutationResponse } from '@/types/api'

describe('GithubMutationCard', () => {
  const mockSucceededMutation: GithubMutationResponse = {
    id: 'mutation-uuid-1',
    organizationId: 'org-uuid-1',
    repositoryId: 'repo-uuid-1',
    taskId: 'task-uuid-1',
    proposedChangeId: 'proposal-uuid-1',
    proposedChangeVersion: 1,
    approvalId: 'approval-uuid-1',
    sandboxExecutionId: 'sandbox-uuid-1',
    baseRevision: 'main',
    branchName: 'devflow/task-1/change-1',
    commitSha: 'a1b2c3d4e5f6g7h8',
    pullRequestNumber: 42,
    pullRequestUrl: 'https://github.com/devflow-platform/auth-service/pull/42',
    status: 'SUCCEEDED',
    failureCode: null,
    failureMessage: null,
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:02:00Z',
    version: 3,
  }

  it('renders successful mutation with branch, commit SHA, and safe PR link', () => {
    render(
      <GithubMutationCard
        mutation={mockSucceededMutation}
        repositoryFullName="devflow-platform/auth-service"
      />
    )

    expect(screen.getByText('GitHub Remote Mutation')).toBeInTheDocument()
    expect(screen.getByText('Succeeded')).toBeInTheDocument()
    expect(screen.getByText('devflow/task-1/change-1')).toBeInTheDocument()
    expect(screen.getByText('a1b2c3d')).toBeInTheDocument()

    const prLink = screen.getByRole('link', { name: /PR #42/i })
    expect(prLink).toBeInTheDocument()
    expect(prLink).toHaveAttribute(
      'href',
      'https://github.com/devflow-platform/auth-service/pull/42'
    )
    expect(prLink).toHaveAttribute('target', '_blank')
    expect(prLink).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('renders running mutation with Live Sync indicator and pending items', () => {
    const runningMutation: GithubMutationResponse = {
      ...mockSucceededMutation,
      status: 'RUNNING',
      commitSha: null,
      pullRequestNumber: null,
      pullRequestUrl: null,
    }

    render(<GithubMutationCard mutation={runningMutation} />)

    expect(screen.getByText('Executing')).toBeInTheDocument()
    expect(screen.getByText('Live Sync (3s)')).toBeInTheDocument()
    expect(screen.getByText('Pending commit')).toBeInTheDocument()
    expect(screen.getByText('Pending PR opening')).toBeInTheDocument()
  })

  it('renders failed mutation with failure code, failure message, and triggers retry', async () => {
    const user = userEvent.setup()
    const onRetryMock = vi.fn()

    const failedMutation: GithubMutationResponse = {
      ...mockSucceededMutation,
      status: 'FAILED',
      failureCode: 'BRANCH_CONFLICT',
      failureMessage: 'Target branch already exists and diverges from base revision',
    }

    render(
      <GithubMutationCard
        mutation={failedMutation}
        onRetry={onRetryMock}
        canRetry={true}
      />
    )

    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('GitHub Mutation Execution Failed')).toBeInTheDocument()
    expect(screen.getByText('BRANCH_CONFLICT')).toBeInTheDocument()
    expect(
      screen.getByText('Target branch already exists and diverges from base revision')
    ).toBeInTheDocument()

    const retryBtn = screen.getByRole('button', { name: /Retry Mutation/i })
    expect(retryBtn).toBeInTheDocument()
    await user.click(retryBtn)
    expect(onRetryMock).toHaveBeenCalled()
  })

  it('handles safe URL sanitization when PR url contains malicious protocol', () => {
    const unsafeMutation: GithubMutationResponse = {
      ...mockSucceededMutation,
      pullRequestNumber: 99,
      pullRequestUrl: 'javascript:alert(1)',
    }

    render(<GithubMutationCard mutation={unsafeMutation} />)

    // Unsafe URLs should be sanitized by getSafeGithubUrl (returns null)
    expect(screen.queryByRole('link', { name: /PR #99/i })).toBeNull()
    expect(screen.getByText('PR #99')).toBeInTheDocument()
  })
})
