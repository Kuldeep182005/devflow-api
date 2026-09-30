import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RegisterPage } from './RegisterPage'
import { AuthProvider } from '@/features/auth/AuthContext'
import { authApi } from '@/api/auth'
import { ApiClientError } from '@/api/client'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
  },
}))

describe('RegisterPage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  const renderRegisterPage = () => {
    return render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <Routes>
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/dashboard" element={<div>Dashboard Page</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )
  }

  it('validates client-side constraints (required email, password min length)', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    const submitBtn = screen.getByRole('button', { name: /create account/i })
    await user.click(submitBtn)

    expect(screen.getByText('Email address is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(screen.getByText('Confirm your password')).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('validates password minimum length of 8 characters', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    await user.type(screen.getByLabelText(/email address/i), 'newuser@devflow.local')
    await user.type(screen.getByLabelText(/^password/i), 'short')
    await user.type(screen.getByLabelText(/confirm password/i), 'short')

    const submitBtn = screen.getByRole('button', { name: /create account/i })
    await user.click(submitBtn)

    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('validates password confirmation mismatch', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    await user.type(screen.getByLabelText(/email address/i), 'newuser@devflow.local')
    await user.type(screen.getByLabelText(/^password/i), 'password123')
    await user.type(screen.getByLabelText(/confirm password/i), 'mismatch456')

    const submitBtn = screen.getByRole('button', { name: /create account/i })
    await user.click(submitBtn)

    expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('submits registration successfully and logs user into dashboard', async () => {
    const user = userEvent.setup()
    const mockAuthResponse = {
      accessToken: 'new_token_789',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '11111111-2222-3333-4444-555555555555',
      email: 'ada@devflow.local',
      displayName: 'Ada Lovelace',
    }

    vi.mocked(authApi.register).mockResolvedValueOnce(mockAuthResponse)

    renderRegisterPage()

    await user.type(screen.getByLabelText(/display name/i), 'Ada Lovelace')
    await user.type(screen.getByLabelText(/email address/i), 'ada@devflow.local')
    await user.type(screen.getByLabelText(/^password/i), 'securePassword123')
    await user.type(screen.getByLabelText(/confirm password/i), 'securePassword123')

    const submitBtn = screen.getByRole('button', { name: /create account/i })
    await user.click(submitBtn)

    expect(authApi.register).toHaveBeenCalledWith({
      email: 'ada@devflow.local',
      password: 'securePassword123',
      displayName: 'Ada Lovelace',
    })

    await waitFor(() => {
      expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
    })
  })

  it('handles 409 conflict when email is already registered', async () => {
    const user = userEvent.setup()

    vi.mocked(authApi.register).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Email already registered',
        code: 'EMAIL_ALREADY_REGISTERED',
        status: 409,
        requestId: 'req_conflict_1',
      })
    )

    renderRegisterPage()

    await user.type(screen.getByLabelText(/email address/i), 'existing@devflow.local')
    await user.type(screen.getByLabelText(/^password/i), 'securePassword123')
    await user.type(screen.getByLabelText(/confirm password/i), 'securePassword123')

    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(
        screen.getByText('This email is already registered. Please sign in instead.')
      ).toBeInTheDocument()
      expect(
        screen.getByText('An account with this email address already exists.')
      ).toBeInTheDocument()
    })
  })

  it('handles backend 400 validation error', async () => {
    const user = userEvent.setup()

    vi.mocked(authApi.register).mockRejectedValueOnce(
      new ApiClientError({
        message: 'Validation failed',
        code: 'VALIDATION_ERROR',
        status: 400,
        violations: [{ field: 'displayName', message: 'must not exceed 120 characters' }],
      })
    )

    renderRegisterPage()

    await user.type(screen.getByLabelText(/email address/i), 'user@devflow.local')
    await user.type(screen.getByLabelText(/^password/i), 'securePassword123')
    await user.type(screen.getByLabelText(/confirm password/i), 'securePassword123')

    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText('must not exceed 120 characters')).toBeInTheDocument()
    })
  })
})
