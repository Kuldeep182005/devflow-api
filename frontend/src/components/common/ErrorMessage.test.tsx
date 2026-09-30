import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ErrorMessage } from './ErrorMessage'

describe('ErrorMessage component', () => {
  it('renders safe error message, title, and reference tags', () => {
    render(
      <ErrorMessage
        title="Invalid Organization"
        message="The slug already exists."
        code="SLUG_CONFLICT"
        requestId="req_12345"
      />
    )

    expect(screen.getByText('Invalid Organization')).toBeInTheDocument()
    expect(screen.getByText('The slug already exists.')).toBeInTheDocument()
    expect(screen.getByText('Code: SLUG_CONFLICT')).toBeInTheDocument()
    expect(screen.getByText('Ref: req_12345')).toBeInTheDocument()
  })

  it('triggers retry callback when retry button is clicked', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()

    render(
      <ErrorMessage
        message="Network error"
        onRetry={onRetry}
      />
    )

    const retryBtn = screen.getByRole('button', { name: /retry/i })
    await user.click(retryBtn)

    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
