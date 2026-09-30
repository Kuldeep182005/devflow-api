import React from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { FileQuestion } from 'lucide-react'

export const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-[#0b0f17] flex flex-col items-center justify-center p-6 text-center">
      <div className="h-16 w-16 rounded-full bg-slate-800/80 flex items-center justify-center text-slate-400 mb-6">
        <FileQuestion className="h-8 w-8" />
      </div>
      <h1 className="text-3xl font-bold text-white tracking-tight">404</h1>
      <p className="text-sm font-semibold text-slate-300 mt-2">Resource Not Found</p>
      <p className="text-xs text-slate-400 mt-1 max-w-sm">
        The route or resource you requested does not exist or has been moved.
      </p>
      <div className="mt-6">
        <Link to="/dashboard">
          <Button variant="secondary" size="sm">
            Back to Dashboard
          </Button>
        </Link>
      </div>
    </div>
  )
}
