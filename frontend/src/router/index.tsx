import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootLayout } from '@/layouts/RootLayout'
import { ProtectedRoute } from '@/router/ProtectedRoute'
import { PublicRoute } from '@/router/PublicRoute'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { OrganizationsPage } from '@/pages/OrganizationsPage'
import { OrganizationDetailPage } from '@/pages/OrganizationDetailPage'
import { RepositoriesPage } from '@/pages/RepositoriesPage'
import { RepositoryDetailPage } from '@/pages/RepositoryDetailPage'
import { TasksPage } from '@/pages/TasksPage'
import { TaskDetailPage } from '@/pages/TaskDetailPage'
import { ApprovalsPage } from '@/pages/ApprovalsPage'
import { ApprovalDetailPage } from '@/pages/ApprovalDetailPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

export const router = createBrowserRouter([
  // Public Routes (redirect to /dashboard if logged in)
  {
    path: '/login',
    element: (
      <PublicRoute>
        <LoginPage />
      </PublicRoute>
    ),
  },
  {
    path: '/register',
    element: (
      <PublicRoute>
        <RegisterPage />
      </PublicRoute>
    ),
  },

  // Protected App Routes
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <RootLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: <DashboardPage />,
      },
      {
        path: 'organizations',
        element: <OrganizationsPage />,
      },
      {
        path: 'organizations/:organizationId',
        element: <OrganizationDetailPage />,
      },
      {
        path: 'organizations/:organizationId/repositories',
        element: <RepositoriesPage />,
      },
      {
        path: 'organizations/:organizationId/repositories/:repositoryId',
        element: <RepositoryDetailPage />,
      },
      {
        path: 'organizations/:organizationId/tasks',
        element: <TasksPage />,
      },
      {
        path: 'organizations/:organizationId/tasks/:taskId',
        element: <TaskDetailPage />,
      },
      {
        path: 'organizations/:organizationId/approvals',
        element: <ApprovalsPage />,
      },
      {
        path: 'organizations/:organizationId/approvals/:approvalId',
        element: <ApprovalDetailPage />,
      },
      {
        path: 'repositories',
        element: <RepositoriesPage />,
      },
      {
        path: 'tasks',
        element: <TasksPage />,
      },
      {
        path: 'approvals',
        element: <ApprovalsPage />,
      },
    ],
  },

  // Catch-all 404 Route
  {
    path: '*',
    element: <NotFoundPage />,
  },
])
