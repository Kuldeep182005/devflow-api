import { describe, it, expect } from 'vitest'
import { getSafeGithubUrl, getSafeGithubPrUrl } from './urls'

describe('getSafeGithubUrl', () => {
  it('constructs a safe https URL for standard owner/repo format', () => {
    expect(getSafeGithubUrl('devflow-inc/api-core')).toBe('https://github.com/devflow-inc/api-core')
    expect(getSafeGithubUrl('octocat/Hello-World')).toBe('https://github.com/octocat/Hello-World')
    expect(getSafeGithubUrl('my_org/repo.js')).toBe('https://github.com/my_org/repo.js')
  })

  it('rejects empty, null, or undefined values', () => {
    expect(getSafeGithubUrl('')).toBeNull()
    expect(getSafeGithubUrl('   ')).toBeNull()
    expect(getSafeGithubUrl(null)).toBeNull()
    expect(getSafeGithubUrl(undefined)).toBeNull()
  })

  it('rejects malicious or invalid URLs to prevent injection', () => {
    expect(getSafeGithubUrl('javascript:alert(1)')).toBeNull()
    expect(getSafeGithubUrl('https://evil.com/attacker/repo')).toBeNull()
    expect(getSafeGithubUrl('../../../etc/passwd')).toBeNull()
    expect(getSafeGithubUrl('foo/bar/baz')).toBeNull()
    expect(getSafeGithubUrl('foo//bar')).toBeNull()
    expect(getSafeGithubUrl('foo\\bar')).toBeNull()
    expect(getSafeGithubUrl('foo/<script>')).toBeNull()
    expect(getSafeGithubUrl('foo bar/repo')).toBeNull()
  })
})

describe('getSafeGithubPrUrl', () => {
  it('validates and returns legitimate GitHub PR URLs', () => {
    expect(
      getSafeGithubPrUrl('https://github.com/devflow-platform/auth-service/pull/42')
    ).toBe('https://github.com/devflow-platform/auth-service/pull/42')
    expect(
      getSafeGithubPrUrl('https://github.com/facebook/react/pull/12345')
    ).toBe('https://github.com/facebook/react/pull/12345')
  })

  it('rejects empty or null values', () => {
    expect(getSafeGithubPrUrl('')).toBeNull()
    expect(getSafeGithubPrUrl(null)).toBeNull()
    expect(getSafeGithubPrUrl(undefined)).toBeNull()
  })

  it('rejects invalid domains, schemes, or formats', () => {
    expect(getSafeGithubPrUrl('http://github.com/org/repo/pull/1')).toBeNull()
    expect(getSafeGithubPrUrl('https://evil.com/org/repo/pull/1')).toBeNull()
    expect(getSafeGithubPrUrl('javascript:alert(1)')).toBeNull()
    expect(getSafeGithubPrUrl('https://github.com/org/repo/pull/abc')).toBeNull()
    expect(getSafeGithubPrUrl('https://github.com/org/repo/pull/1/../../etc')).toBeNull()
  })
})
