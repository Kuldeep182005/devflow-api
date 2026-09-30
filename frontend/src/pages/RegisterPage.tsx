import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Terminal, Lock, Mail, User, ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { useAuth } from '@/hooks/useAuth'
import { authApi } from '@/api/auth'
import { ApiClientError } from '@/api/client'

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [fieldErrors, setFieldErrors] = useState<{
    email?: string
    displayName?: string
    password?: string
    confirmPassword?: string
  }>({})
  const [generalError, setGeneralError] = useState<{
    message: string
    code?: string
    requestId?: string
  } | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: {
      email?: string
      displayName?: string
      password?: string
      confirmPassword?: string
    } = {}

    const trimmedEmail = email.trim()
    if (!trimmedEmail) {
      errors.email = 'Email address is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errors.email = 'Please enter a valid email address'
    } else if (trimmedEmail.length > 320) {
      errors.email = 'Email address must not exceed 320 characters'
    }

    const trimmedName = displayName.trim()
    if (trimmedName && trimmedName.length > 120) {
      errors.displayName = 'Display name must not exceed 120 characters'
    }

    if (!password) {
      errors.password = 'Password is required'
    } else if (password.length < 8) {
      errors.password = 'Password must be at least 8 characters'
    } else if (password.length > 128) {
      errors.password = 'Password must not exceed 128 characters'
    }

    if (!confirmPassword) {
      errors.confirmPassword = 'Confirm your password'
    } else if (confirmPassword !== password) {
      errors.confirmPassword = 'Passwords do not match'
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (isSubmitting) return

    setGeneralError(null)
    setFieldErrors({})

    if (!validate()) {
      return
    }

    setIsSubmitting(true)

    try {
      const response = await authApi.register({
        email: email.trim(),
        password,
        displayName: displayName.trim() || undefined,
      })

      // Backend returns AuthResponse on registration - establish session and navigate
      login(response)
      navigate('/dashboard', { replace: true })
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        if (err.isConflict || err.code === 'EMAIL_ALREADY_REGISTERED') {
          setFieldErrors((prev) => ({
            ...prev,
            email: 'This email is already registered. Please sign in instead.',
          }))
          setGeneralError({
            message: 'An account with this email address already exists.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.violations && err.violations.length > 0) {
          const fieldMap: { email?: string; displayName?: string; password?: string } = {}
          err.violations.forEach((v) => {
            if (v.field === 'email') fieldMap.email = v.message
            if (v.field === 'displayName') fieldMap.displayName = v.message
            if (v.field === 'password') fieldMap.password = v.message
          })
          setFieldErrors((prev) => ({ ...prev, ...fieldMap }))
          setGeneralError({
            message: 'Please resolve the highlighted validation errors.',
            code: err.code,
            requestId: err.requestId,
          })
        } else {
          setGeneralError({
            message: err.message,
            code: err.code,
            requestId: err.requestId,
          })
        }
      } else {
        setGeneralError({
          message: 'An unexpected connection error occurred. Please try again.',
        })
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#0b0f17] flex flex-col justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        {/* Branding */}
        <div className="inline-flex h-12 w-12 rounded-xl bg-blue-600/20 border border-blue-500/40 items-center justify-center text-blue-400 mb-4 shadow-sm">
          <Terminal className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Create your account</h1>
        <p className="mt-2 text-xs text-slate-400">
          Join DevFlow to automate software engineering workflows with isolated verification
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-[#111622] py-8 px-6 shadow-xl border border-slate-800/90 rounded-xl sm:px-10">
          {/* General API Error Alert */}
          {generalError && (
            <div className="mb-6">
              <ErrorMessage
                title="Registration failed"
                message={generalError.message}
                code={generalError.code}
                requestId={generalError.requestId}
              />
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {/* Display Name Field (Optional, max 120) */}
            <div>
              <Label htmlFor="displayName">
                Display name <span className="text-slate-500 font-normal">(optional)</span>
              </Label>
              <div className="relative">
                <Input
                  id="displayName"
                  name="displayName"
                  type="text"
                  autoComplete="name"
                  placeholder="Ada Lovelace"
                  value={displayName}
                  disabled={isSubmitting}
                  error={fieldErrors.displayName}
                  onChange={(e) => {
                    setDisplayName(e.target.value)
                    if (fieldErrors.displayName) {
                      setFieldErrors((prev) => ({ ...prev, displayName: undefined }))
                    }
                  }}
                  className="pl-9"
                />
                <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-500 pointer-events-none" />
              </div>
            </div>

            {/* Email Field */}
            <div>
              <Label htmlFor="email" required>
                Email address
              </Label>
              <div className="relative">
                <Input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  placeholder="name@company.com"
                  value={email}
                  disabled={isSubmitting}
                  error={fieldErrors.email}
                  onChange={(e) => {
                    setEmail(e.target.value)
                    if (fieldErrors.email) {
                      setFieldErrors((prev) => ({ ...prev, email: undefined }))
                    }
                  }}
                  className="pl-9"
                />
                <Mail className="absolute left-3 top-2.5 h-4 w-4 text-slate-500 pointer-events-none" />
              </div>
            </div>

            {/* Password Field (min 8, max 128) */}
            <div>
              <Label htmlFor="password" required>
                Password <span className="text-slate-500 font-normal">(min 8 characters)</span>
              </Label>
              <div className="relative">
                <Input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  required
                  placeholder="••••••••"
                  value={password}
                  disabled={isSubmitting}
                  error={fieldErrors.password}
                  onChange={(e) => {
                    setPassword(e.target.value)
                    if (fieldErrors.password) {
                      setFieldErrors((prev) => ({ ...prev, password: undefined }))
                    }
                  }}
                  className="pl-9"
                />
                <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-500 pointer-events-none" />
              </div>
            </div>

            {/* Confirm Password Field */}
            <div>
              <Label htmlFor="confirmPassword" required>
                Confirm password
              </Label>
              <div className="relative">
                <Input
                  id="confirmPassword"
                  name="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  required
                  placeholder="••••••••"
                  value={confirmPassword}
                  disabled={isSubmitting}
                  error={fieldErrors.confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value)
                    if (fieldErrors.confirmPassword) {
                      setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }))
                    }
                  }}
                  className="pl-9"
                />
                <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-500 pointer-events-none" />
              </div>
            </div>

            {/* Submit Button */}
            <div className="pt-3">
              <Button
                type="submit"
                variant="primary"
                size="md"
                isLoading={isSubmitting}
                disabled={isSubmitting}
                className="w-full"
              >
                {!isSubmitting && (
                  <>
                    Create Account
                    <ArrowRight className="h-4 w-4 ml-1.5" />
                  </>
                )}
                {isSubmitting && 'Creating account...'}
              </Button>
            </div>
          </form>

          {/* Login Navigation Link */}
          <div className="mt-6 pt-5 border-t border-slate-800/80 text-center text-xs">
            <span className="text-slate-400">Already have an account? </span>
            <Link
              to="/login"
              className="text-blue-400 hover:text-blue-300 font-medium transition-colors"
            >
              Sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
