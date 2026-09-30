import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import { AuthProvider } from '@/features/auth/AuthContext'
import { authApi } from '@/api/auth'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
  },
}))

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  const renderLoginPage = () => {
    return render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/dashboard" element={<div>Dashboard Page</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )
  }

  it('validates required fields on client-side before submitting', async () => {
    const user = userEvent.setup()
    renderLoginPage()

    const submitBtn = screen.getByRole('button', { name: /sign in/i })
    await user.click(submitBtn)

    expect(screen.getByText('Email address is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('validates invalid email format', async () => {
    const user = userEvent.setup()
    renderLoginPage()

    const emailInput = screen.getByLabelText(/email address/i)
    await user.type(emailInput, 'not-an-email')

    const submitBtn = screen.getByRole('button', { name: /sign in/i })
    await user.click(submitBtn)

    expect(screen.getByText('Please enter a valid email address')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('successfully authenticates and redirects to dashboard', async () => {
    const user = userEvent.setup()
    const mockAuthResponse = {
      accessToken: 'test_token_123',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Lead Engineer',
    }

    vi.mocked(authApi.login).mockResolvedValueOnce(mockAuthResponse)

    renderLoginPage()

    await user.type(screen.getByLabelText(/email address/i), 'engineer@devflow.local')
    await user.type(screen.getByLabelText(/password/i), 'secretPassword123')

    const submitBtn = screen.getByRole('button', { name: /sign in/i })
    await user.click(submitBtn)

    expect(authApi.login).toHaveBeenCalledWith({
      email: 'engineer@devflow.local',
      password: 'secretPassword123',
    })

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
    })
  })

  it('displays user-friendly error message on backend 401 Unauthorized', async () => {
    const user = userEvent.setup()

    vi.mocked(authApi.login).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Invalid credentials',
        code: 'INVALID_CREDENTIALS',
        status: 401,
        requestId: 'req_auth_fail_1',
      })
    )

    renderLoginPage()

    await user.type(screen.getByLabelText(/email address/i), 'engineer@devflow.local')
    await user.type(screen.getByLabelText(/password/i), 'wrongPassword')

    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/Invalid email or password. Please verify your credentials and try again./i)
      ).toBeInTheDocument()
    })
    expect(screen.getByText('Code: INVALID_CREDENTIALS')).toBeInTheDocument()
    expect(screen.getByText('Ref: req_auth_fail_1')).toBeInTheDocument()
  })

  it('displays field violations when backend returns 400 validation error', async () => {
    const user = userEvent.setup()

    vi.mocked(authApi.login).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Request validation failed',
        code: 'VALIDATION_ERROR',
        status: 400,
        violations: [{ field: 'email', message: 'must be a well-formed email address' }],
      })
    )

    renderLoginPage()

    await user.type(screen.getByLabelText(/email address/i), 'engineer@devflow.local')
    await user.type(screen.getByLabelText(/password/i), 'password')

    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(screen.getByText('must be a well-formed email address')).toBeInTheDocument()
    })
  })

  it('disables submit button and shows loading state during request', async () => {
    const user = userEvent.setup()

    let resolvePromise: (value: unknown) => void
    const pendingPromise = new Promise((resolve) => {
      resolvePromise = resolve
    })

    vi.mocked(authApi.login).mockImplementationOnce(() => pendingPromise as never)

    renderLoginPage()

    await user.type(screen.getByLabelText(/email address/i), 'engineer@devflow.local')
    await user.type(screen.getByLabelText(/password/i), 'password123')

    const submitBtn = screen.getByRole('button', { name: /sign in/i })
    await user.click(submitBtn)

    expect(screen.getByRole('button')).toBeDisabled()
    expect(screen.getByText('Authenticating...')).toBeInTheDocument()

    resolvePromise!({
      accessToken: 'token_123',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Dev',
    })

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
    })
  })
})
