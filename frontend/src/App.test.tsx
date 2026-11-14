import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import App from './App'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('App routes', () => {
  it('shows the sign-in page at /login', () => {
    renderAt('/login')

    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
  })

  it('shows the sign-up page at /register', () => {
    renderAt('/register')

    expect(screen.getByRole('heading', { name: /create your account/i })).toBeInTheDocument()
  })

  it('sends the root path to the sign-in page', () => {
    renderAt('/')

    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown paths', () => {
    renderAt('/no-such-page')

    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument()
  })
})
