import React from 'react'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/features/auth/AuthContext'
import { OrganizationProvider } from '@/features/organizations/OrganizationContext'
import { router } from '@/router'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000, // 30 seconds
      retry: (failureCount, error) => {
        // Do not retry 401, 403, 404, or 409
        if (
          typeof error === 'object' &&
          error !== null &&
          'status' in error &&
          typeof error.status === 'number'
        ) {
          if ([401, 403, 404, 409].includes(error.status)) {
            return false
          }
        }
        return failureCount < 2
      },
      refetchOnWindowFocus: false,
    },
  },
})

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <OrganizationProvider>
          <RouterProvider router={router} />
        </OrganizationProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}

export default App
