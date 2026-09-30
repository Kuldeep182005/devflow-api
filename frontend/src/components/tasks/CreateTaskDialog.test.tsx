import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CreateTaskDialog } from './CreateTaskDialog'
import { tasksApi } from '@/api/tasks'
import { repositoriesApi } from '@/api/repositories'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/tasks', () => ({
  tasksApi: {
    create: vi.fn(),
  },
}))

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    list: vi.fn(),
  },
}))

describe('CreateTaskDialog', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-123'
  const mockRepos = {
    content: [
      {
        id: 'repo-1',
        organizationId: mockOrgId,
        provider: 'GITHUB' as const,
        externalId: '100',
        fullName: 'org/repo-one',
        defaultBranch: 'main',
        active: true,
        createdAt: '2026-09-30T10:00:00Z',
        updatedAt: '2026-09-30T10:00:00Z',
      },
      {
        id: 'repo-2',
        organizationId: mockOrgId,
        provider: 'GITHUB' as const,
        externalId: '200',
        fullName: 'org/repo-two',
        defaultBranch: 'develop',
        active: true,
        createdAt: '2026-09-30T10:00:00Z',
        updatedAt: '2026-09-30T10:00:00Z',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    size: 100,
    page: 0,
    first: true,
    last: true,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    vi.mocked(repositoriesApi.list).mockResolvedValue(mockRepos)
  })

  const renderComponent = (props: Partial<Parameters<typeof CreateTaskDialog>[0]> = {}) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <CreateTaskDialog
          isOpen={true}
          onClose={vi.fn()}
          organizationId={mockOrgId}
          {...props}
        />
      </QueryClientProvider>
    )
  }

  it('renders form fields when open', async () => {
    renderComponent()

    expect(screen.getByText('Dispatch AI Task')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('org/repo-one (main)')).toBeInTheDocument()
    })

    expect(screen.getByLabelText(/Target Repository/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Task Title/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Instructions & Context/i)).toBeInTheDocument()
  })

  it('does not render when isOpen is false', () => {
    renderComponent({ isOpen: false })
    expect(screen.queryByText('Dispatch AI Task')).toBeNull()
  })

  it('validates required fields when submitted empty', async () => {
    const user = userEvent.setup()
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('org/repo-one (main)')).toBeInTheDocument()
    })

    const submitBtn = screen.getByRole('button', { name: /Dispatch Task/i })
    await user.click(submitBtn)

    expect(screen.getByText('Please select a target repository')).toBeInTheDocument()
    expect(screen.getByText('Task title is required')).toBeInTheDocument()
    expect(tasksApi.create).not.toHaveBeenCalled()
  })

  it('dispatches task successfully when form is valid', async () => {
    const user = userEvent.setup()
    const handleClose = vi.fn()
    const handleSuccess = vi.fn()

    const mockCreatedTask = {
      id: 'task-new-123',
      organizationId: mockOrgId,
      repositoryId: 'repo-1',
      createdByUserId: 'user-1',
      sourceType: 'MANUAL' as const,
      sourceReference: null,
      title: 'Fix issue with login tokens',
      description: 'Check auth service expiration',
      status: 'QUEUED' as const,
      branchName: null,
      currentStep: null,
      failureCode: null,
      failureMessage: null,
      failedAt: null,
      createdAt: '2026-09-30T10:00:00Z',
      updatedAt: '2026-09-30T10:00:00Z',
      startedAt: null,
      finishedAt: null,
    }

    vi.mocked(tasksApi.create).mockResolvedValue(mockCreatedTask)

    renderComponent({ onClose: handleClose, onSuccess: handleSuccess })

    await waitFor(() => {
      expect(screen.getByText('org/repo-one (main)')).toBeInTheDocument()
    })

    const select = screen.getByLabelText(/Target Repository/i)
    await user.selectOptions(select, 'repo-1')

    const titleInput = screen.getByPlaceholderText(/Fix authentication token/i)
    await user.type(titleInput, 'Fix issue with login tokens')

    const descInput = screen.getByPlaceholderText(/Provide specific requirements/i)
    await user.type(descInput, 'Check auth service expiration')

    const submitBtn = screen.getByRole('button', { name: /Dispatch Task/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(tasksApi.create).toHaveBeenCalledWith(mockOrgId, {
        repositoryId: 'repo-1',
        title: 'Fix issue with login tokens',
        description: 'Check auth service expiration',
      })
      expect(handleClose).toHaveBeenCalled()
      expect(handleSuccess).toHaveBeenCalledWith(mockCreatedTask)
    })
  })

  it('handles 400 validation error from backend', async () => {
    const user = userEvent.setup()
    const apiError = new ApiClientError({
      message: 'Validation failed',
      status: 400,
      code: 'VALIDATION_FAILED',
      requestId: 'req-400',
      violations: [{ field: 'title', message: 'Title is too short' }],
    })
    vi.mocked(tasksApi.create).mockRejectedValue(apiError)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('org/repo-one (main)')).toBeInTheDocument()
    })

    await user.selectOptions(screen.getByLabelText(/Target Repository/i), 'repo-1')
    await user.type(screen.getByPlaceholderText(/Fix authentication token/i), 'Fix')

    await user.click(screen.getByRole('button', { name: /Dispatch Task/i }))

    await waitFor(() => {
      expect(screen.getByText('Title is too short')).toBeInTheDocument()
    })
  })

  it('handles 403 permission denied from backend', async () => {
    const user = userEvent.setup()
    const apiError = new ApiClientError({
      message: 'Forbidden',
      status: 403,
      code: 'TASK_CREATE_FORBIDDEN',
      requestId: 'req-403',
    })
    vi.mocked(tasksApi.create).mockRejectedValue(apiError)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('org/repo-one (main)')).toBeInTheDocument()
    })

    await user.selectOptions(screen.getByLabelText(/Target Repository/i), 'repo-1')
    await user.type(screen.getByPlaceholderText(/Fix authentication token/i), 'Valid Title')

    await user.click(screen.getByRole('button', { name: /Dispatch Task/i }))

    await waitFor(() => {
      expect(
        screen.getByText('You do not have permission to dispatch tasks in this organization.')
      ).toBeInTheDocument()
    })
  })

  it('handles 409 conflict from backend', async () => {
    const user = userEvent.setup()
    const apiError = new ApiClientError({
      message: 'Conflict',
      status: 409,
      code: 'CONCURRENT_TASK_LIMIT',
      requestId: 'req-409',
    })
    vi.mocked(tasksApi.create).mockRejectedValue(apiError)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('org/repo-one (main)')).toBeInTheDocument()
    })

    await user.selectOptions(screen.getByLabelText(/Target Repository/i), 'repo-1')
    await user.type(screen.getByPlaceholderText(/Fix authentication token/i), 'Valid Title')

    await user.click(screen.getByRole('button', { name: /Dispatch Task/i }))

    await waitFor(() => {
      expect(
        screen.getByText('A conflicting task or repository state prevented task dispatch.')
      ).toBeInTheDocument()
    })
  })
})
