import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OrganizationDetailPage } from './OrganizationDetailPage'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/organizations', () => ({
  organizationsApi: {
    get: vi.fn(),
    listMembers: vi.fn(),
    addMember: vi.fn(),
    updateMember: vi.fn(),
    removeMember: vi.fn(),
  },
}))

describe('OrganizationDetailPage', () => {
  let queryClient: QueryClient

  const mockOrg = {
    id: 'org-uuid-1234',
    name: 'DevFlow Infrastructure',
    slug: 'devflow-infra',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockMembers = {
    content: [
      {
        organizationId: 'org-uuid-1234',
        userId: 'user-current-111',
        email: 'alice@devflow.local',
        displayName: 'Alice Owner',
        role: 'OWNER' as const,
        createdAt: '2026-09-30T10:00:00Z',
      },
      {
        organizationId: 'org-uuid-1234',
        userId: 'user-member-222',
        email: 'bob@devflow.local',
        displayName: 'Bob Member',
        role: 'MEMBER' as const,
        createdAt: '2026-09-30T10:30:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
  }

  const mockAuthContext: AuthContextValue = {
    session: {
      accessToken: 'token_123',
      tokenType: 'Bearer',
      expiresAt: Date.now() + 3600000,
      userId: 'user-current-111',
      email: 'alice@devflow.local',
      displayName: 'Alice Owner',
    },
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.mocked(organizationsApi.get).mockResolvedValue(mockOrg)
    vi.mocked(organizationsApi.listMembers).mockResolvedValue(mockMembers)
    vi.clearAllMocks()
  })

  const renderDetailPage = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={mockAuthContext}>
          <MemoryRouter initialEntries={['/organizations/org-uuid-1234']}>
            <Routes>
              <Route path="/organizations/:organizationId" element={<OrganizationDetailPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders organization metadata and current user role badge', async () => {
    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'DevFlow Infrastructure', level: 1 })).toBeInTheDocument()
      expect(screen.getByText('devflow-infra')).toBeInTheDocument()
      expect(screen.getByText('Your Role: OWNER')).toBeInTheDocument()
    })
  })

  it('renders team member table with roles and user identifiers', async () => {
    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText('Alice Owner')).toBeInTheDocument()
      expect(screen.getByText('(You)')).toBeInTheDocument()
      expect(screen.getByText('bob@devflow.local')).toBeInTheDocument()
      expect(screen.getByText('Bob Member')).toBeInTheDocument()
    })
  })

  it('adds a member to the organization with valid UUID', async () => {
    const user = userEvent.setup()
    vi.mocked(organizationsApi.addMember).mockResolvedValueOnce({
      organizationId: 'org-uuid-1234',
      userId: '11111111-2222-3333-4444-555555555555',
      email: 'charlie@devflow.local',
      displayName: 'Charlie',
      role: 'ADMIN',
      createdAt: '2026-09-30T12:00:00Z',
    })

    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'DevFlow Infrastructure', level: 1 })).toBeInTheDocument()
    })

    const addMemberBtn = screen.getByRole('button', { name: /add member/i })
    await user.click(addMemberBtn)

    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeInTheDocument()

    // Test UUID validation
    const userIdInput = within(dialog).getByLabelText(/user uuid/i)
    await user.type(userIdInput, 'not-a-uuid')
    await user.click(within(dialog).getByRole('button', { name: /^add member$/i }))

    expect(within(dialog).getByText(/please enter a valid uuid format/i)).toBeInTheDocument()

    // Clear and enter valid UUID
    await user.clear(userIdInput)
    await user.type(userIdInput, '11111111-2222-3333-4444-555555555555')

    await user.click(within(dialog).getByRole('button', { name: /^add member$/i }))

    expect(organizationsApi.addMember).toHaveBeenCalledWith('org-uuid-1234', {
      userId: '11111111-2222-3333-4444-555555555555',
      role: 'MEMBER',
    })
  })

  it('updates member role via dropdown', async () => {
    const user = userEvent.setup()
    vi.mocked(organizationsApi.updateMember).mockResolvedValueOnce({
      organizationId: 'org-uuid-1234',
      userId: 'user-member-222',
      email: 'bob@devflow.local',
      displayName: 'Bob Member',
      role: 'ADMIN',
      createdAt: '2026-09-30T10:30:00Z',
    })

    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText('Bob Member')).toBeInTheDocument()
    })

    const roleSelects = screen.getAllByRole('combobox')
    // Select second dropdown (Bob's role)
    await user.selectOptions(roleSelects[1], 'ADMIN')

    expect(organizationsApi.updateMember).toHaveBeenCalledWith(
      'org-uuid-1234',
      'user-member-222',
      { role: 'ADMIN' }
    )
  })

  it('requires confirmation before removing a member', async () => {
    const user = userEvent.setup()
    vi.mocked(organizationsApi.removeMember).mockResolvedValueOnce()

    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText('Bob Member')).toBeInTheDocument()
    })

    const removeBtns = screen.getAllByRole('button', { name: /remove/i })
    await user.click(removeBtns[0])

    expect(screen.getByText('Confirm Member Removal')).toBeInTheDocument()

    const confirmBtn = screen.getByRole('button', { name: /^remove member$/i })
    await user.click(confirmBtn)

    expect(organizationsApi.removeMember).toHaveBeenCalledTimes(1)
  })

  it('displays authorization error when backend returns 403 Forbidden for member modification', async () => {
    const user = userEvent.setup()
    vi.mocked(organizationsApi.updateMember).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Access to this resource is forbidden',
        code: 'FORBIDDEN',
        status: 403,
      })
    )

    renderDetailPage()

    await waitFor(() => {
      expect(screen.getByText('Bob Member')).toBeInTheDocument()
    })

    const roleSelects = screen.getAllByRole('combobox')
    await user.selectOptions(roleSelects[1], 'ADMIN')

    await waitFor(() => {
      expect(
        screen.getByText(/You do not have permission to modify roles in this organization./i)
      ).toBeInTheDocument()
    })
  })
})
