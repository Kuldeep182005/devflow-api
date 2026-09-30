import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CreateRepositoryDialog } from './CreateRepositoryDialog'
import { repositoriesApi } from '@/api/repositories'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    create: vi.fn(),
  },
}))

describe('CreateRepositoryDialog', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1234'
  const onClose = vi.fn()
  const onCreated = vi.fn()

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.clearAllMocks()
  })

  const renderDialog = (isOpen = true) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <CreateRepositoryDialog
          isOpen={isOpen}
          onClose={onClose}
          organizationId={mockOrgId}
          onCreated={onCreated}
        />
      </QueryClientProvider>
    )
  }

  it('renders modal dialog with required fields and GitHub provider', () => {
    renderDialog()

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Register Repository' })).toBeInTheDocument()
    expect(screen.getByText('GitHub')).toBeInTheDocument()
    expect(screen.getByLabelText(/repository full name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/external provider id/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/default branch/i)).toHaveValue('main')
  })

  it('validates required fields and fullName format before submission', async () => {
    const user = userEvent.setup()
    renderDialog()

    const submitBtn = screen.getByRole('button', { name: /register repository/i })
    await user.click(submitBtn)

    expect(screen.getByText(/full repository name is required/i)).toBeInTheDocument()
    expect(screen.getByText(/external provider id is required/i)).toBeInTheDocument()

    // Test invalid fullName format without slash
    const fullNameInput = screen.getByLabelText(/repository full name/i)
    await user.type(fullNameInput, 'invalid-no-slash')
    await user.click(submitBtn)

    expect(screen.getByText(/format must be owner\/repository/i)).toBeInTheDocument()
    expect(repositoriesApi.create).not.toHaveBeenCalled()
  })

  it('submits valid repository creation successfully', async () => {
    const user = userEvent.setup()
    const mockCreated = {
      id: 'repo-uuid-999',
      organizationId: mockOrgId,
      provider: 'GITHUB' as const,
      externalId: '102938475',
      fullName: 'devflow-org/api-server',
      defaultBranch: 'main',
      active: true,
      createdAt: '2026-09-30T12:00:00Z',
      updatedAt: '2026-09-30T12:00:00Z',
    }
    vi.mocked(repositoriesApi.create).mockResolvedValueOnce(mockCreated)

    renderDialog()

    await user.type(screen.getByLabelText(/repository full name/i), 'devflow-org/api-server')
    await user.type(screen.getByLabelText(/external provider id/i), '102938475')

    await user.click(screen.getByRole('button', { name: /register repository/i }))

    await waitFor(() => {
      expect(repositoriesApi.create).toHaveBeenCalledWith(mockOrgId, {
        provider: 'GITHUB',
        fullName: 'devflow-org/api-server',
        externalId: '102938475',
        defaultBranch: 'main',
      })
      expect(onCreated).toHaveBeenCalledWith(mockCreated)
      expect(onClose).toHaveBeenCalled()
    })
  })

  it('handles backend 409 conflict when repository already exists', async () => {
    const user = userEvent.setup()
    vi.mocked(repositoriesApi.create).mockRejectedValueOnce(
      new ApiClientError({
        message: 'The repository already exists in this organization',
        code: 'REPOSITORY_ALREADY_EXISTS',
        status: 409,
      })
    )

    renderDialog()

    await user.type(screen.getByLabelText(/repository full name/i), 'devflow-org/existing-repo')
    await user.type(screen.getByLabelText(/external provider id/i), '999999')
    await user.click(screen.getByRole('button', { name: /register repository/i }))

    await waitFor(() => {
      expect(
        screen.getByText('This repository already exists in this organization.')
      ).toBeInTheDocument()
    })
  })

  it('handles backend 403 forbidden error when user lacks owner or admin permissions', async () => {
    const user = userEvent.setup()
    vi.mocked(repositoriesApi.create).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Only owners and admins can register repositories',
        code: 'FORBIDDEN',
        status: 403,
      })
    )

    renderDialog()

    await user.type(screen.getByLabelText(/repository full name/i), 'devflow-org/repo')
    await user.type(screen.getByLabelText(/external provider id/i), '12345')
    await user.click(screen.getByRole('button', { name: /register repository/i }))

    await waitFor(() => {
      expect(
        screen.getByText('Only organization Owners and Admins can register repositories.')
      ).toBeInTheDocument()
    })
  })
})
