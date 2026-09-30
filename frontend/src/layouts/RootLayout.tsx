import React, { useState } from 'react'
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  Building2,
  FolderGit2,
  CheckSquare2,
  ShieldAlert,
  LogOut,
  Activity,
  Terminal,
  Menu,
  X,
} from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { OrganizationSelector } from '@/components/organizations/OrganizationSelector'
import { cn } from '@/lib/utils'
import { healthApi } from '@/api/health'
import { useQuery } from '@tanstack/react-query'

export const RootLayout: React.FC = () => {
  const { session, logout } = useAuth()
  const { activeOrganization } = useActiveOrganization()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  // Background health check against /api/v1/health
  const { data: health } = useQuery({
    queryKey: ['system-health'],
    queryFn: () => healthApi.check(),
    staleTime: 60000,
    refetchInterval: 60000,
  })

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  // Close mobile drawer when route changes
  const handleNavClick = () => {
    setMobileMenuOpen(false)
  }

  // Dynamically route scoped features to active organization if present
  const repositoriesTo = activeOrganization
    ? `/organizations/${activeOrganization.id}/repositories`
    : '/repositories'

  const tasksTo = activeOrganization
    ? `/organizations/${activeOrganization.id}/tasks`
    : '/tasks'

  const approvalsTo = activeOrganization
    ? `/organizations/${activeOrganization.id}/approvals`
    : '/approvals'

  const navItems = [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, exact: true },
    { to: '/organizations', label: 'Organizations', icon: Building2, exact: false },
    { to: tasksTo, label: 'Tasks', icon: CheckSquare2, exact: false, matchPrefix: '/tasks' },
    { to: approvalsTo, label: 'Approvals', icon: ShieldAlert, exact: false, matchPrefix: '/approvals' },
    { to: repositoriesTo, label: 'Repositories', icon: FolderGit2, exact: false, matchPrefix: '/repositories' },
  ]

  const isItemActive = (to: string, matchPrefix?: string) => {
    if (location.pathname === to) return true
    if (matchPrefix && location.pathname.includes(matchPrefix)) return true
    if (to !== '/dashboard' && location.pathname.startsWith(to)) return true
    return false
  }

  return (
    <div className="min-h-screen bg-[#0b0f17] text-slate-100 flex flex-col md:flex-row">
      {/* Mobile Header Bar */}
      <header className="h-14 border-b border-slate-800/80 bg-[#0d121c] flex md:hidden items-center justify-between px-4 z-30 shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="h-7 w-7 rounded-md bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
            <Terminal className="h-4 w-4" />
          </div>
          <span className="font-bold text-sm tracking-tight text-white">DevFlow</span>
        </div>
        <button
          onClick={() => setMobileMenuOpen((prev) => !prev)}
          className="p-2 text-slate-400 hover:text-white rounded-md hover:bg-slate-800/80"
          aria-label={mobileMenuOpen ? 'Close navigation' : 'Open navigation'}
        >
          {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </header>

      {/* Persistent Sidebar (Desktop) & Overlay Drawer (Mobile) */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-64 border-r border-slate-800/80 bg-[#0d121c] flex flex-col transition-transform duration-200 md:static md:translate-x-0',
          mobileMenuOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        )}
      >
        {/* Brand Header */}
        <div className="h-16 flex items-center px-5 border-b border-slate-800/60 gap-3 shrink-0">
          <div className="h-8 w-8 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
            <Terminal className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="font-bold text-sm tracking-tight text-white">DevFlow</span>
              <span className="text-[10px] uppercase font-mono px-1.5 py-0.2 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                v1
              </span>
            </div>
            <p className="text-[10px] text-slate-400 truncate">Autonomous Engineering</p>
          </div>
        </div>

        {/* Organization Selector */}
        <div className="p-3 border-b border-slate-800/60">
          <OrganizationSelector />
        </div>

        {/* Main Navigation Links */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto" aria-label="Main Navigation">
          {navItems.map((item) => {
            const Icon = item.icon
            const active = isItemActive(item.to, item.matchPrefix)

            return (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={handleNavClick}
                className={cn(
                  'flex items-center gap-3 px-3 py-2 text-xs font-medium rounded-md transition-colors',
                  active
                    ? 'bg-blue-600/15 text-blue-400 border border-blue-500/20 font-semibold'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>

        {/* System Health Status & User Profile Area */}
        <div className="p-4 border-t border-slate-800/60 space-y-3 bg-[#0a0e16]/50 shrink-0">
          {/* API Status Indicator */}
          <div className="flex items-center justify-between text-[11px] text-slate-400">
            <span className="flex items-center gap-1.5">
              <Activity className="h-3.5 w-3.5 text-slate-400" />
              API Status
            </span>
            <span className="flex items-center gap-1.5 font-mono text-[10px]">
              <span
                className={cn(
                  'h-1.5 w-1.5 rounded-full',
                  health?.status === 'UP' ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'
                )}
              />
              {health?.status || 'ONLINE'}
            </span>
          </div>

          {/* User profile & Logout */}
          <div className="flex items-center justify-between pt-2 border-t border-slate-800/40">
            <div className="min-w-0 pr-2">
              <p className="text-xs font-medium text-slate-200 truncate">
                {session?.displayName || 'Engineer'}
              </p>
              <p className="text-[10px] text-slate-400 truncate font-mono">{session?.email}</p>
            </div>
            <button
              onClick={handleLogout}
              title="Sign out"
              aria-label="Sign out"
              className="p-1.5 text-slate-400 hover:text-red-400 hover:bg-slate-800 rounded transition-colors"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Backdrop overlay for mobile drawer */}
      {mobileMenuOpen && (
        <div
          className="fixed inset-0 bg-black/60 z-30 md:hidden"
          onClick={() => setMobileMenuOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Main Content Viewport */}
      <main className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
