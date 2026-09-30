import React, { useState } from 'react'
import { Building2, ChevronDown, Plus, Check } from 'lucide-react'
import { useActiveOrganization } from '@/hooks/useActiveOrganization'
import { CreateOrganizationDialog } from './CreateOrganizationDialog'
import { cn } from '@/lib/utils'

export const OrganizationSelector: React.FC<{ className?: string }> = ({ className }) => {
  const {
    organizations,
    activeOrganization,
    setActiveOrganizationId,
    isLoading,
  } = useActiveOrganization()

  const [isOpen, setIsOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)

  if (isLoading) {
    return (
      <div className={cn('h-10 w-full rounded-lg bg-slate-800/40 animate-pulse', className)} />
    )
  }

  return (
    <>
      <div className={cn('relative', className)}>
        {/* Selector Button */}
        <button
          type="button"
          onClick={() => setIsOpen((prev) => !prev)}
          className="w-full flex items-center justify-between p-2.5 rounded-lg border border-slate-800 bg-[#0e131d] hover:bg-slate-800/40 text-left transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500/80"
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          aria-label="Select organization"
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="h-6 w-6 rounded bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400 shrink-0">
              <Building2 className="h-3.5 w-3.5" />
            </div>
            <div className="min-w-0">
              <p className="text-xs font-semibold text-slate-100 truncate">
                {activeOrganization ? activeOrganization.name : 'No Organization'}
              </p>
              <p className="text-[10px] text-slate-400 font-mono truncate">
                {activeOrganization ? activeOrganization.slug : 'Create one to begin'}
              </p>
            </div>
          </div>
          <ChevronDown className="h-3.5 w-3.5 text-slate-400 shrink-0 ml-1.5" />
        </button>

        {/* Dropdown Menu */}
        {isOpen && (
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={() => setIsOpen(false)}
              aria-hidden="true"
            />
            <div
              role="listbox"
              className="absolute left-0 right-0 top-full mt-1.5 z-50 rounded-lg border border-slate-800 bg-[#111622] p-1.5 shadow-xl text-xs space-y-1"
            >
              <div className="px-2 py-1 text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                Switch Organization
              </div>

              {organizations.length === 0 ? (
                <div className="px-3 py-2 text-slate-400 text-center text-xs">
                  No organizations found
                </div>
              ) : (
                organizations.map((org) => {
                  const isSelected = activeOrganization?.id === org.id
                  return (
                    <button
                      key={org.id}
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => {
                        setActiveOrganizationId(org.id)
                        setIsOpen(false)
                      }}
                      className={cn(
                        'w-full flex items-center justify-between px-2.5 py-2 rounded-md transition-colors text-left',
                        isSelected
                          ? 'bg-blue-600/15 text-blue-400 font-medium'
                          : 'text-slate-300 hover:bg-slate-800/60 hover:text-white'
                      )}
                    >
                      <div className="min-w-0">
                        <p className="truncate">{org.name}</p>
                        <p className="text-[10px] text-slate-400 font-mono truncate">{org.slug}</p>
                      </div>
                      {isSelected && <Check className="h-3.5 w-3.5 shrink-0 ml-2 text-blue-400" />}
                    </button>
                  )
                })
              )}

              <div className="pt-1 border-t border-slate-800/60 mt-1">
                <button
                  type="button"
                  onClick={() => {
                    setIsOpen(false)
                    setIsCreateOpen(true)
                  }}
                  className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-md text-blue-400 hover:bg-blue-600/10 transition-colors font-medium text-left"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>Create Organization</span>
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      <CreateOrganizationDialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
    </>
  )
}
