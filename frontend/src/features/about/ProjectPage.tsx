import { usePageTitle } from '@/lib/usePageTitle'
import { Prose } from './Prose'

/** Where the technical story lives, so the storefront itself stays a store. */
export default function ProjectPage() {
  usePageTitle('About this project')
  return (
    <Prose
      title="About this project"
      lead="TrustKart is a portfolio project: a virtual technology store built with the engineering a real one would need."
    >
      <h2>Why virtual commerce</h2>
      <p>
        Real payments would mean real risk and real compliance for a demo. Virtual funds keep the full
        shopping journey while removing financial data entirely. The money-handling code is still written as
        if it mattered: BigDecimal amounts, row locks, idempotency keys, an append-only ledger and
        all-or-nothing transactions.
      </p>
      <h2>Architecture</h2>
      <ul>
        <li>
          Java 21 and Spring Boot 4 as a modular monolith (catalog, search, cart, wallet, purchase,
          collection, auth, shopper).
        </li>
        <li>
          PostgreSQL with Flyway migrations: JSONB product specifications with a per-category registry,
          full-text search, CHECK constraints on money.
        </li>
        <li>
          Redis for distributed rate limiting (Bucket4j) and Google sign-in state. Signing out takes effect at
          once: every request checks its session in PostgreSQL.
        </li>
        <li>React, TypeScript, TanStack Query and Tailwind CSS, built from an approved design mockup.</li>
      </ul>
      <h2>Checkout correctness</h2>
      <ul>
        <li>
          The browser sends product ids and quantities only. The server prices every line, computes the total
          and ignores anything else.
        </li>
        <li>
          One transaction locks the wallet row, checks funds, commits stock with conditional updates, records
          the purchase, debits the ledger and clears the cart.
        </li>
        <li>
          Idempotency keys make double clicks and retries return the original purchase instead of charging
          twice.
        </li>
        <li>
          Tested with concurrent requests against real PostgreSQL, including a failure injected after the
          debit to prove rollback.
        </li>
      </ul>
      <h2>Security</h2>
      <ul>
        <li>
          Argon2id password hashes, account lockout, per-IP rate limits, and identical responses for unknown
          emails and wrong passwords.
        </li>
        <li>
          10-minute access tokens in HttpOnly cookies, rotating refresh tokens with reuse detection, and
          immediate revocation.
        </li>
        <li>
          CSRF protection on every state-changing request, strict security headers and an explicit CORS
          allow-list.
        </li>
        <li>
          Every owned resource is looked up by owner, so another shopper’s ids behave like missing ones.
        </li>
      </ul>
      <h2>Testing</h2>
      <p>
        JUnit and Testcontainers integration tests run against real PostgreSQL and Redis, covering
        authentication attacks, IDOR attempts, price manipulation, concurrency and rollback. The frontend uses
        Vitest, and Playwright drives full shopping journeys in a real browser; CI runs all of it.
      </p>
      <h2>What’s not built yet</h2>
      <p>
        Semantic (AI) search, two-factor authentication, passkeys, the admin console and the PC builder are
        planned. They are labelled as such wherever the interface mentions them.
      </p>
    </Prose>
  )
}
