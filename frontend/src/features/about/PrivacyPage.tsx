import { usePageTitle } from '@/lib/usePageTitle'
import { Prose } from './Prose'

export default function PrivacyPage() {
  usePageTitle('Privacy')
  return (
    <Prose
      title="Privacy"
      lead="TrustKart collects as little as it can. This is a portfolio project, not a commercial service."
    >
      <h2>What we store</h2>
      <ul>
        <li>
          If you create an account: your email address, the name you choose, and a password hash (Argon2id).
          Never the password itself.
        </li>
        <li>Sign-in history for your security: time, IP address and browser type of each attempt.</li>
        <li>Your cart, wishlists, virtual wallet ledger and virtual purchases.</li>
        <li>
          Delivery addresses you choose to save or enter at checkout. They are only shown back to you on your
          orders; nothing is ever shipped. Delete saved addresses any time from your account.
        </li>
        <li>
          Guests get an anonymous random cookie instead of an account. Guest data is deleted after 30 days of
          inactivity.
        </li>
      </ul>
      <h2>What we never collect</h2>
      <ul>
        <li>Card numbers, bank details, billing addresses or any other financial information.</li>
      </ul>
      <h2>In your browser only</h2>
      <p>
        Recent searches, recently viewed products, your compare picks and your theme choice are stored in this
        browser’s local storage and never sent to TrustKart. Clearing site data removes them.
      </p>
      <h2>Cookies</h2>
      <p>
        TrustKart uses strictly necessary cookies only: sign-in session cookies (HttpOnly), a guest shopper
        cookie, and a CSRF protection token. There are no advertising or tracking cookies and no third-party
        analytics.
      </p>
    </Prose>
  )
}
