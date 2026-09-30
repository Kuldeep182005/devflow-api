import React, { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { Terminal, Lock, Mail, ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { useAuth } from '@/hooks/useAuth'
import { authApi } from '@/api/auth'
import { ApiClientError } from '@/api/client'

export const LoginPage: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({})
  const [generalError, setGeneralError] = useState<{
    message: string
    code?: string
    requestId?: string
  } | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: { email?: string; password?: string } = {}
    const trimmedEmail = email.trim()

    if (!trimmedEmail) {
      errors.email = 'Email address is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errors.email = 'Please enter a valid email address'
    } else if (trimmedEmail.length > 320) {
      errors.email = 'Email address must not exceed 320 characters'
    }

    if (!password) {
      errors.password = 'Password is required'
    } else if (password.length > 128) {
      errors.password = 'Password must not exceed 128 characters'
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
      const response = await authApi.login({
        email: email.trim(),
        password,
      })

      login(response)

      const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/dashboard'
      navigate(from, { replace: true })
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        if (err.isUnauthorized) {
          setGeneralError({
            message: 'Invalid email or password. Please verify your credentials and try again.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.isRateLimited) {
          const retryMessage = err.retryAfterSeconds
            ? `Too many attempts. Please wait ${err.retryAfterSeconds} seconds before trying again.`
            : 'Too many login attempts. Please wait a moment and try again.'
          setGeneralError({
            message: retryMessage,
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.violations && err.violations.length > 0) {
          const fieldMap: { email?: string; password?: string } = {}
          err.violations.forEach((v) => {
            if (v.field === 'email') fieldMap.email = v.message
            if (v.field === 'password') fieldMap.password = v.message
          })
          setFieldErrors(fieldMap)
          setGeneralError({
            message: 'Please resolve the highlighted field errors.',
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
        <h1 className="text-2xl font-bold tracking-tight text-white">Sign in to DevFlow</h1>
        <p className="mt-2 text-xs text-slate-400">
          AI-assisted software engineering & automated proposal verification
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-[#111622] py-8 px-6 shadow-xl border border-slate-800/90 rounded-xl sm:px-10">
          {/* General API Error Alert */}
          {generalError && (
            <div className="mb-6">
              <ErrorMessage
                title="Authentication failed"
                message={generalError.message}
                code={generalError.code}
                requestId={generalError.requestId}
              />
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-5">
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
                  autoFocus
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

            {/* Password Field */}
            <div>
              <div className="flex items-center justify-between">
                <Label htmlFor="password" required>
                  Password
                </Label>
              </div>
              <div className="relative">
                <Input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
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

            {/* Submit Button */}
            <div className="pt-2">
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
                    Sign In
                    <ArrowRight className="h-4 w-4 ml-1.5" />
                  </>
                )}
                {isSubmitting && 'Authenticating...'}
              </Button>
            </div>
          </form>

          {/* Registration Navigation Link */}
          <div className="mt-6 pt-5 border-t border-slate-800/80 text-center text-xs">
            <span className="text-slate-400">Don't have an account yet? </span>
            <Link
              to="/register"
              className="text-blue-400 hover:text-blue-300 font-medium transition-colors"
            >
              Create an account
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
