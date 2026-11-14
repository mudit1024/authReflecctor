import { Navigate, Route, Routes } from 'react-router'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { AccountPage } from '@/pages/AccountPage'
import { AppsPage } from '@/pages/admin/AppsPage'
import { AppFormPage } from '@/pages/admin/AppFormPage'

// Every path here must also be listed in the backend's SpaController,
// so a browser refresh on that URL gets index.html instead of a 404.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/account" element={<AccountPage />} />
      <Route path="/admin/apps" element={<AppsPage />} />
      <Route path="/admin/apps/new" element={<AppFormPage />} />
      <Route path="/admin/apps/:clientId" element={<AppFormPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
