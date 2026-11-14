import { render, screen } from '@testing-library/react'
import { AuthLayout } from './AuthLayout'

describe('AuthLayout', () => {
  it('shows the page title, subtitle and content', () => {
    render(
      <AuthLayout title="Welcome back" subtitle="Sign in with your AuthReflector account">
        <p>form goes here</p>
      </AuthLayout>,
    )

    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(screen.getByText('Sign in with your AuthReflector account')).toBeInTheDocument()
    expect(screen.getByText('form goes here')).toBeInTheDocument()
  })

  it('names the app the user is signing in to, when one is known', () => {
    render(<AuthLayout title="Welcome back" clientName="SplitMate" />)

    expect(screen.getByText(/to continue to/i)).toHaveTextContent('to continue to SplitMate')
  })

  it('does not mention an app when the user came to AuthReflector directly', () => {
    render(<AuthLayout title="Welcome back" />)

    expect(screen.queryByText(/to continue to/i)).not.toBeInTheDocument()
  })
})
