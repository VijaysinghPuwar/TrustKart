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
