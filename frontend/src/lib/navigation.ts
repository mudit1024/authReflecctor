// A full page navigation (not a React Router route change): after sign-in the next URL may be a server
// URL, e.g. continuing an app's /oauth2/authorize request. Its own module so tests can replace it.
export function goTo(url: string): void {
  window.location.assign(url)
}
