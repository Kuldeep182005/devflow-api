import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RepositoryDetailPage } from './RepositoryDetailPage'
import { repositoriesApi } from '@/api/repositories'
import { organizationsApi } from '@/api/organizations'

vi.mock('@/api/repositories', () => ({
  repositoriesApi: {
    get: vi.fn(),
  },
}))

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    get: vi.fn(),
  },
}))

describe('RepositoryDetailPage', () => {
  let queryClient: QueryClient
  const mockOrgId = 'org-uuid-1234'
  const mockRepoId = 'repo-uuid-9999'

  const mockOrg = {
    id: mockOrgId,
    name: 'DevFlow Infrastructure',
    slug: 'devflow-infra',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockRepo = {
    id: mockRepoId,
    organizationId: mockOrgId,
    provider: 'GITHUB' as const,
    externalId: '102938475',
    fullName: 'devflow-infra/core-api',
    defaultBranch: 'main',
    active: true,
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  let writeTextMock = vi.fn()

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.mocked(repositoriesApi.get).mockResolvedValue(mockRepo)
    vi.mocked(organizationsApi.get).mockResolvedValue(mockOrg)
    vi.clearAllMocks()

    // Mock clipboard safely
    writeTextMock = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: writeTextMock,
      },
      writable: true,
      configurable: true,
    })
  })

  const renderDetailPage = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter
          initialEntries={[`/organizations/${mockOrgId}/repositories/${mockRepoId}`]}
        >
          <Routes>
            <Route
              path="/organizations/:organizationId/repositories/:repositoryId"
              element={<RepositoryDetailPage />}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  it('renders repository details, provider badge, branch, and status', async () => {
    renderDetailPage()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: 'devflow-infra/core-api', level: 1 })
      ).toBeInTheDocument()
      expect(screen.getByText('GITHUB')).toBeInTheDocument()
      expect(screen.getByText('Active')).toBeInTheDocument()
      expect(screen.getAllByText('main')[0]).toBeInTheDocument()
      expect(screen.getByText('102938475')).toBeInTheDocument()
    })
  })

  it('renders safe external GitHub link with target _blank and noopener', async () => {
    renderDetailPage()

    await waitFor(() => {
      const githubLink = screen.getByRole('link', { name: /view on github/i })
      expect(githubLink).toHaveAttribute('href', 'https://github.com/devflow-infra/core-api')
      expect(githubLink).toHaveAttribute('target', '_blank')
      expect(githubLink).toHaveAttribute('rel', 'noopener noreferrer')
    })
  })

  it('copies repository ID and external ID to clipboard', async () => {
    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText(mockRepoId)).toBeInTheDocument()
    })

    const copyRepoBtn = screen.getByRole('button', { name: /copy repository id/i })
    fireEvent.click(copyRepoBtn)

    expect(writeTextMock).toHaveBeenCalledWith(mockRepoId)

    const copyExtBtn = screen.getByRole('button', { name: /copy external id/i })
    fireEvent.click(copyExtBtn)

    expect(writeTextMock).toHaveBeenCalledWith('102938475')
  })

  it('renders engineering workflow shortcuts to tasks, approvals, and organization overview', async () => {
    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText('Organization Tasks')).toBeInTheDocument()
      expect(screen.getByText('Change Approvals')).toBeInTheDocument()
      expect(screen.getByText('Organization Overview')).toBeInTheDocument()
    })
  })

  it('handles 404 repository not found error gracefully', async () => {
    vi.mocked(repositoriesApi.get).mockRejectedValueOnce(
      new Error('The repository does not exist')
    )

    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText('Repository not found')).toBeInTheDocument()
      expect(screen.getByText('The repository does not exist')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /back to repositories/i })).toBeInTheDocument()
    })
  })
})
