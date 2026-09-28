/**
 * Optional system notifications. Off by default: the shopper turns them on from the notifications page,
 * which is the only place permission is ever requested (never on page load). The choice is per browser.
 */
const KEY = 'tk-browser-alerts'

export function browserAlertsSupported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window
}

export function browserAlertsEnabled(): boolean {
  if (!browserAlertsSupported() || Notification.permission !== 'granted') return false
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

/** Returns whether alerts ended up enabled (false if the browser or the shopper said no). */
export async function setBrowserAlerts(on: boolean): Promise<boolean> {
  if (!browserAlertsSupported()) return false
  if (on && Notification.permission !== 'granted') {
    const result = await Notification.requestPermission()
    if (result !== 'granted') return false
  }
  try {
    localStorage.setItem(KEY, on ? '1' : '0')
  } catch {
    return false
  }
  return on
}
