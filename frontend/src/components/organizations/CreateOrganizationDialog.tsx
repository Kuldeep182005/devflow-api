import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { ErrorMessage } from '@/components/common/ErrorMessage'
import { organizationsApi } from '@/api/organizations'
import { ApiClientError } from '@/api/client'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'

export interface CreateOrganizationDialogProps {
  isOpen: boolean
  onClose: () => void
}

export const CreateOrganizationDialog: React.FC<CreateOrganizationDialogProps> = ({
  isOpen,
  onClose,
}) => {
  const navigate = useNavigate()
  const { setActiveOrganizationId, refetchOrganizations } = useActiveOrganization()

  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [isSlugManuallyEdited, setIsSlugManuallyEdited] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{ name?: string; slug?: string }>({})
  const [generalError, setGeneralError] = useState<{
    message: string
    code?: string
    requestId?: string
  } | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Auto-slugify name
  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newName = e.target.value
    setName(newName)
    if (!isSlugManuallyEdited) {
      const generated = newName
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
      setSlug(generated)
    }
    if (fieldErrors.name) {
      setFieldErrors((prev) => ({ ...prev, name: undefined }))
    }
  }

  const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setIsSlugManuallyEdited(true)
    setSlug(e.target.value.toLowerCase())
    if (fieldErrors.slug) {
      setFieldErrors((prev) => ({ ...prev, slug: undefined }))
    }
  }

  const validate = (): boolean => {
    const errors: { name?: string; slug?: string } = {}
    const trimmedName = name.trim()
    const trimmedSlug = slug.trim()

    if (!trimmedName) {
      errors.name = 'Organization name is required'
    } else if (trimmedName.length > 120) {
      errors.name = 'Organization name must not exceed 120 characters'
    }

    if (!trimmedSlug) {
      errors.slug = 'Slug is required'
    } else if (trimmedSlug.length > 120) {
      errors.slug = 'Slug must not exceed 120 characters'
    } else if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(trimmedSlug)) {
      errors.slug = 'Slug must contain only lowercase letters, numbers, and hyphens (e.g. engineering-team)'
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (isSubmitting) return

    setGeneralError(null)
    setFieldErrors({})

    if (!validate()) return

    setIsSubmitting(true)

    try {
      const created = await organizationsApi.create({
        name: name.trim(),
        slug: slug.trim(),
      })

      refetchOrganizations()
      setActiveOrganizationId(created.id)
      onClose()
      // Reset form
      setName('')
      setSlug('')
      setIsSlugManuallyEdited(false)
      navigate(`/organizations/${created.id}`)
    } catch (err: unknown) {
      if (err instanceof ApiClientError) {
        if (err.isConflict || err.code === 'SLUG_ALREADY_EXISTS') {
          setFieldErrors((prev) => ({
            ...prev,
            slug: 'This slug is already taken. Please choose another.',
          }))
          setGeneralError({
            message: 'An organization with this slug already exists.',
            code: err.code,
            requestId: err.requestId,
          })
        } else if (err.violations && err.violations.length > 0) {
          const fieldMap: { name?: string; slug?: string } = {}
          err.violations.forEach((v) => {
            if (v.field === 'name') fieldMap.name = v.message
            if (v.field === 'slug') fieldMap.slug = v.message
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
          message: 'An unexpected connection error occurred.',
        })
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title="Create New Organization"
      description="Set up an organization workspace to connect repositories and run AI engineering tasks."
    >
      {generalError && (
        <div className="mb-4">
          <ErrorMessage
            title="Could not create organization"
            message={generalError.message}
            code={generalError.code}
            requestId={generalError.requestId}
          />
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <div>
          <Label htmlFor="org-name" required>
            Organization name
          </Label>
          <Input
            id="org-name"
            name="name"
            type="text"
            required
            autoFocus
            placeholder="DevFlow Core"
            value={name}
            disabled={isSubmitting}
            error={fieldErrors.name}
            onChange={handleNameChange}
          />
        </div>

        <div>
          <Label htmlFor="org-slug" required>
            Organization slug
          </Label>
          <Input
            id="org-slug"
            name="slug"
            type="text"
            required
            placeholder="devflow-core"
            value={slug}
            disabled={isSubmitting}
            error={fieldErrors.slug}
            onChange={handleSlugChange}
          />
          <p className="mt-1 text-[11px] text-slate-500">
            Lowercase alphanumeric characters and hyphens only.
          </p>
        </div>

        <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800/80">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onClose}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            isLoading={isSubmitting}
            disabled={isSubmitting}
          >
            Create Organization
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
