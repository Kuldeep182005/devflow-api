import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RootLayout } from './RootLayout'
import { AuthContext, type AuthContextValue } from '@/features/auth/context'
import { OrganizationContext, type OrganizationContextValue } from '@/features/organizations/context'
import { healthApi } from '@/api/health'

vi.mock('@/api/health', () => ({
  healthApi: {
    check: vi.fn(),
  },
}))

describe('RootLayout Application Shell', () => {
  let queryClient: QueryClient

  const mockSession = {
    accessToken: 'test_token',
    tokenType: 'Bearer',
    expiresAt: Date.now() + 3600000,
    userId: 'user-123',
    email: 'engineer@devflow.local',
    displayName: 'Senior Engineer',
  }

  const mockOrg = {
    id: 'org-111',
    name: 'DevFlow Primary',
    slug: 'devflow-primary',
    createdAt: '2026-09-30T10:00:00Z',
    updatedAt: '2026-09-30T10:00:00Z',
  }

  const mockAuthContext: AuthContextValue = {
    session: mockSession,
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  const mockOrgContext: OrganizationContextValue = {
    organizations: [mockOrg],
    activeOrganization: mockOrg,
    activeOrganizationId: mockOrg.id,
    setActiveOrganizationId: vi.fn(),
    isLoading: false,
    error: null,
    refetchOrganizations: vi.fn(),
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.mocked(healthApi.check).mockResolvedValue({ status: 'UP', checkedAt: '2026-09-30T10:00:00Z' })
    vi.clearAllMocks()
  })

  const renderShell = (initialRoute = '/dashboard') => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={mockAuthContext}>
          <OrganizationContext.Provider value={mockOrgContext}>
            <MemoryRouter initialEntries={[initialRoute]}>
              <Routes>
                <Route path="/" element={<RootLayout />}>
                  <Route path="dashboard" element={<div>Dashboard Content View</div>} />
                  <Route path="organizations" element={<div>Organizations View</div>} />
                  <Route path="organizations/:id/tasks" element={<div>Org Tasks View</div>} />
                  <Route path="login" element={<div>Login Page Target</div>} />
                </Route>
              </Routes>
            </MemoryRouter>
          </OrganizationContext.Provider>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }

  it('renders persistent navigation links, branding, and user session info', () => {
    renderShell('/dashboard')

    expect(screen.getAllByText('DevFlow').length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /organizations/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /tasks/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /approvals/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /repositories/i })).toBeInTheDocument()

    expect(screen.getByText('Senior Engineer')).toBeInTheDocument()
    expect(screen.getByText('engineer@devflow.local')).toBeInTheDocument()
  })

  it('highlights the active navigation link based on current route', () => {
    renderShell('/dashboard')

    const dashboardLink = screen.getByRole('link', { name: /dashboard/i })
    expect(dashboardLink.className).toContain('text-blue-400')
  })

  it('triggers logout action when sign out button is clicked', async () => {
    const user = userEvent.setup()
    renderShell('/dashboard')

    const logoutBtn = screen.getByRole('button', { name: /sign out/i })
    await user.click(logoutBtn)

    expect(mockAuthContext.logout).toHaveBeenCalledTimes(1)
  })

  it('toggles mobile navigation drawer', async () => {
    const user = userEvent.setup()
    renderShell('/dashboard')

    const menuToggle = screen.getByRole('button', { name: /open navigation/i })
    await user.click(menuToggle)

    expect(screen.getByRole('button', { name: /close navigation/i })).toBeInTheDocument()
  })
})
