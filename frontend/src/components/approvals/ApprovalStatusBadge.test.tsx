import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ApprovalStatusBadge } from './ApprovalStatusBadge'

describe('ApprovalStatusBadge', () => {
  it('renders PENDING status badge correctly', () => {
    render(<ApprovalStatusBadge status="PENDING" />)
    expect(screen.getByText('Pending Review')).toBeInTheDocument()
  })

  it('renders APPROVED status badge correctly', () => {
    render(<ApprovalStatusBadge status="APPROVED" />)
    expect(screen.getByText('Approved')).toBeInTheDocument()
  })

  it('renders REJECTED status badge correctly', () => {
    render(<ApprovalStatusBadge status="REJECTED" />)
    expect(screen.getByText('Rejected')).toBeInTheDocument()
  })

  it('renders CANCELLED status badge correctly', () => {
    render(<ApprovalStatusBadge status="CANCELLED" />)
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
  })
})
