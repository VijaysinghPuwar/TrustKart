;(function () {
  var theme = null
  try {
    theme = localStorage.getItem('tk-theme')
  } catch (e) {
    theme = null
  }
  if (theme !== 'light' && theme !== 'dark') {
    theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  document.documentElement.setAttribute('data-theme', theme)
})()

// On the home page, start the catalog request now, while the app bundle is still downloading: the hero image (the
// largest paint) can only be requested once this data arrives. The app picks the promise up once and then forgets it.
;(function () {
  if (location.pathname !== '/' || !window.fetch) return
  window.__tkHome = fetch('/api/v1/catalog/home', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
    .then(function (r) {
      return r.ok ? r.json() : null
    })
    .then(function (home) {
      // Preload the first banner photo with the same srcset/sizes the banner uses, so the browser starts the
      // largest paint's download now instead of after the app renders.
      var first = home && home.heroSlides && home.heroSlides[0]
      if (first && first.image) {
        var link = document.createElement('link')
        link.rel = 'preload'
        link.as = 'image'
        link.setAttribute('imagesrcset', first.image.small + ' 400w, ' + first.image.large + ' 800w')
        link.setAttribute('imagesizes', '(max-width: 768px) 90vw, 400px')
        link.setAttribute('fetchpriority', 'high')
        document.head.appendChild(link)
      }
      return home
    })
    .catch(function () {
      return null
    })
})()
