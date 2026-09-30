/**
 * Validates a GitHub repository full name and constructs a safe HTTPS URL.
 * Prevents protocol injection, open redirects, or script execution.
 */
export function getSafeGithubUrl(fullName: string | null | undefined): string | null {
  if (!fullName) return null
  const trimmed = fullName.trim()

  // Standard GitHub repository naming: :owner/:repo
  // Owner and repo contain alphanumeric characters, underscores, hyphens, and dots.
  // Neither can be empty, and traversal paths like ../ are strictly forbidden.
  const githubRepoPattern = /^[a-zA-Z0-9_.-]+\/[a-zA-Z0-9_.-]+$/

  if (!githubRepoPattern.test(trimmed)) {
    return null
  }

  // Reject malicious traversal segments
  if (trimmed.includes('..') || trimmed.includes('//') || trimmed.includes('\\')) {
    return null
  }

  return `https://github.com/${trimmed}`
}

/**
 * Validates a GitHub pull request URL.
 * Ensures https scheme, github.com host, valid repository format, and valid PR number.
 * Prevents protocol injection, traversal, script execution, or open redirects.
 */
export function getSafeGithubPrUrl(rawUrl: string | null | undefined): string | null {
  if (!rawUrl) return null
  const trimmed = rawUrl.trim()

  // Standard GitHub PR URL format: https://github.com/:owner/:repo/pull/:number
  const githubPrPattern = /^https:\/\/github\.com\/[a-zA-Z0-9_.-]+\/[a-zA-Z0-9_.-]+\/pull\/[0-9]+$/

  if (!githubPrPattern.test(trimmed)) {
    return null
  }

  // Reject malicious traversal or weird segments
  if (trimmed.includes('..') || trimmed.includes('\\')) {
    return null
  }

  return trimmed
}
