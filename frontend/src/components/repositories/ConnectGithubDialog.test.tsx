import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConnectGithubDialog } from './ConnectGithubDialog'
import { repositoriesApi } from '@/api/repositories'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    connect: vi.fn(),
  },
}))

describe('ConnectGithubDialog', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1234'
  const mockOrgSlug = 'devflow-team'
  const onClose = vi.fn()
  const onConnected = vi.fn()

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.clearAllMocks()
  })

  const renderDialog = (isOpen = true) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <ConnectGithubDialog
          isOpen={isOpen}
          onClose={onClose}
          organizationId={mockOrgId}
          organizationSlug={mockOrgSlug}
          onConnected={onConnected}
        />
      </QueryClientProvider>
    )
  }

  it('renders modal with organization slug pre-filled and verifies no credential inputs exist', () => {
    renderDialog()

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Connect GitHub Repository')).toBeInTheDocument()
    expect(screen.getByLabelText(/github owner \/ account/i)).toHaveValue(mockOrgSlug)
    expect(screen.getByLabelText(/repository name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/installation id/i)).toBeInTheDocument()

    // Critical security assertion: ensure no password, secret, token, or private key input exists
    expect(screen.queryByLabelText(/token/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/secret/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/private key/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
  })

  it('validates required repository name before calling API', async () => {
    const user = userEvent.setup()
    renderDialog()

    const submitBtn = screen.getByRole('button', { name: /connect repository/i })
    await user.click(submitBtn)

    expect(screen.getByText(/repository name is required/i)).toBeInTheDocument()
    expect(repositoriesApi.connect).not.toHaveBeenCalled()
  })

  it('validates optional installation ID is a positive integer', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByLabelText(/repository name/i), 'backend-service')
    await user.type(screen.getByLabelText(/installation id/i), '-5')
    await user.click(screen.getByRole('button', { name: /connect repository/i }))

    expect(screen.getByText(/installation id must be a positive integer/i)).toBeInTheDocument()
    expect(repositoriesApi.connect).not.toHaveBeenCalled()
  })

  it('successfully connects repository and executes callbacks', async () => {
    const user = userEvent.setup()
    const mockConnected = {
      id: 'repo-uuid-888',
      organizationId: mockOrgId,
      provider: 'GITHUB' as const,
      externalId: '87654321',
      fullName: 'devflow-team/backend-service',
      defaultBranch: 'main',
      active: true,
      createdAt: '2026-09-30T12:00:00Z',
      updatedAt: '2026-09-30T12:00:00Z',
    }
    vi.mocked(repositoriesApi.connect).mockResolvedValueOnce(mockConnected)

    renderDialog()

    await user.type(screen.getByLabelText(/repository name/i), 'backend-service')
    await user.type(screen.getByLabelText(/installation id/i), '123456')
    await user.click(screen.getByRole('button', { name: /connect repository/i }))

    await waitFor(() => {
      expect(repositoriesApi.connect).toHaveBeenCalledWith(mockOrgId, {
        provider: 'GITHUB',
        owner: 'devflow-team',
        name: 'backend-service',
        installationId: 123456,
      })
      expect(onConnected).toHaveBeenCalledWith(mockConnected)
      expect(onClose).toHaveBeenCalled()
    })
  })

  it('handles GITHUB_INSTALLATION_NOT_FOUND error with clear user message', async () => {
    const user = userEvent.setup()
    vi.mocked(repositoriesApi.connect).mockRejectedValueOnce(
      new ApiClientError({
        message: 'The GitHub App is not installed for this organization',
        code: 'GITHUB_INSTALLATION_NOT_FOUND',
        status: 400,
      })
    )

    renderDialog()

    await user.type(screen.getByLabelText(/repository name/i), 'backend-service')
    await user.click(screen.getByRole('button', { name: /connect repository/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/the github app is not installed for this organization account/i)
      ).toBeInTheDocument()
    })
  })

  it('handles REPOSITORY_ORGANIZATION_MISMATCH error with security explanation', async () => {
    const user = userEvent.setup()
    vi.mocked(repositoriesApi.connect).mockRejectedValueOnce(
      new ApiClientError({
        message: 'The repository does not belong to this organization',
        code: 'REPOSITORY_ORGANIZATION_MISMATCH',
        status: 403,
      })
    )

    renderDialog()

    await user.type(screen.getByLabelText(/repository name/i), 'other-repo')
    await user.click(screen.getByRole('button', { name: /connect repository/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/the repository owner must match your organization slug/i)
      ).toBeInTheDocument()
    })
  })

  it('handles GITHUB_NOT_CONFIGURED 503 error', async () => {
    const user = userEvent.setup()
    vi.mocked(repositoriesApi.connect).mockRejectedValueOnce(
      new ApiClientError({
        message: 'GitHub integration is not configured',
        code: 'GITHUB_NOT_CONFIGURED',
        status: 503,
      })
    )

    renderDialog()

    await user.type(screen.getByLabelText(/repository name/i), 'my-repo')
    await user.click(screen.getByRole('button', { name: /connect repository/i }))

    await waitFor(() => {
      expect(
        screen.getByText('GitHub App integration is not configured on this server.')
      ).toBeInTheDocument()
    })
  })
})
