import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from '@/app/App'
import '@/styles/index.css'

// A tab opened before a deploy asks for code chunks the new deployment no longer serves, and the page it navigates to
// would fail. Reload once to pick up the current build. The timestamp stops a reload loop if a chunk is really missing.
window.addEventListener('vite:preloadError', (event) => {
  const key = 'tk-chunk-reload'
  try {
    if (Date.now() - Number(sessionStorage.getItem(key) ?? 0) < 10_000) return
    sessionStorage.setItem(key, String(Date.now()))
  } catch {
    return // no storage, so no loop guard: let the error page explain instead
  }
  event.preventDefault()
  window.location.reload()
})

const root = document.getElementById('root')
if (!root) throw new Error('Root element #root is missing from index.html')

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
