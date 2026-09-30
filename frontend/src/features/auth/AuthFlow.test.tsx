import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { useAuth } from '@/hooks/useAuth'
import { ProtectedRoute } from '@/router/ProtectedRoute'
import { PublicRoute } from '@/router/PublicRoute'
import { setStoredAuth, getStoredAuth, clearStoredAuth } from '@/lib/auth'

const TestAuthConsumer = () => {
  const { session, isAuthenticated, login, logout } = useAuth()
  return (
    <div>
      <div data-testid="auth-status">{isAuthenticated ? 'AUTHENTICATED' : 'UNAUTHENTICATED'}</div>
      <div data-testid="user-email">{session?.email || 'NONE'}</div>
      <button
        onClick={() =>
          login({
            accessToken: 'test_token',
            tokenType: 'Bearer',
            expiresIn: 3600,
            userId: 'user-1',
            email: 'auth_test@devflow.local',
            displayName: 'Auth Test User',
          })
        }
      >
        Trigger Login
      </button>
      <button onClick={logout}>Trigger Logout</button>
    </div>
  )
}

describe('Auth State & Route Guards Flow', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('updates session and authentication status upon login and clears upon logout', async () => {
    const user = userEvent.setup()

    render(
      <AuthProvider>
        <TestAuthConsumer />
      </AuthProvider>
    )

    expect(screen.getByTestId('auth-status')).toHaveTextContent('UNAUTHENTICATED')
    expect(screen.getByTestId('user-email')).toHaveTextContent('NONE')

    // Trigger Login
    await user.click(screen.getByRole('button', { name: /trigger login/i }))

    expect(screen.getByTestId('auth-status')).toHaveTextContent('AUTHENTICATED')
    expect(screen.getByTestId('user-email')).toHaveTextContent('auth_test@devflow.local')
    expect(getStoredAuth()?.accessToken).toBe('test_token')

    // Trigger Logout
    await user.click(screen.getByRole('button', { name: /trigger logout/i }))

    expect(screen.getByTestId('auth-status')).toHaveTextContent('UNAUTHENTICATED')
    expect(screen.getByTestId('user-email')).toHaveTextContent('NONE')
    expect(getStoredAuth()).toBeNull()
  })

  it('redirects unauthenticated users attempting to access ProtectedRoute to /login', () => {
    clearStoredAuth()

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>Secret Dashboard Content</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>Login Page Guard Target</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(screen.queryByText('Secret Dashboard Content')).not.toBeInTheDocument()
    expect(screen.getByText('Login Page Guard Target')).toBeInTheDocument()
  })

  it('allows authenticated users to access ProtectedRoute', () => {
    setStoredAuth({
      accessToken: 'valid_active_token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Dev',
    })

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>Secret Dashboard Content</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>Login Page</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(screen.getByText('Secret Dashboard Content')).toBeInTheDocument()
  })

  it('redirects authenticated users attempting to access PublicRoute to /dashboard', () => {
    setStoredAuth({
      accessToken: 'valid_active_token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Dev',
    })

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/login"
              element={
                <PublicRoute>
                  <div>Login Form Component</div>
                </PublicRoute>
              }
            />
            <Route path="/dashboard" element={<div>Redirected To Dashboard</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(screen.queryByText('Login Form Component')).not.toBeInTheDocument()
    expect(screen.getByText('Redirected To Dashboard')).toBeInTheDocument()
  })

  it('rejects expired session and redirects on ProtectedRoute', () => {
    // Session expired 10 minutes ago
    const expiredSession = {
      accessToken: 'expired_token_123',
      tokenType: 'Bearer',
      expiresAt: Date.now() - 600000,
      userId: '7b70c36d-1bf9-47e2-892f-b44c0fbceee2',
      email: 'engineer@devflow.local',
      displayName: 'Dev',
    }
    localStorage.setItem('devflow_auth_session', JSON.stringify(expiredSession))

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>Protected Area</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>Login Screen Redirect</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(screen.queryByText('Protected Area')).not.toBeInTheDocument()
    expect(screen.getByText('Login Screen Redirect')).toBeInTheDocument()
  })
})
