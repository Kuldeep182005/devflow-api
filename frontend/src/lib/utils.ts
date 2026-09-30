import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Merges class names safely with Tailwind resolution.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}

/**
 * Generates a safe alphanumeric request ID matching backend pattern ^[a-zA-Z0-9_-]{1,64}$
 */
export function generateRequestId(): string {
  return 'req_' + Math.random().toString(36).substring(2, 10) + Date.now().toString(36)
}

/**
 * Format ISO timestamp into human-readable date string.
 */
export function formatDate(isoString: string | null | undefined): string {
  if (!isoString) return '—'
  try {
    const date = new Date(isoString)
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(date)
  } catch {
    return isoString
  }
}
