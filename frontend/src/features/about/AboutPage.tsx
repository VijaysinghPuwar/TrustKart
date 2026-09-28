import { Link } from 'react-router'
import { usePageTitle } from '@/lib/usePageTitle'
import { Prose } from './Prose'

/** Store Policy: the one place that explains, plainly, how TrustKart works. Linked from the footer and checkout. */
export default function AboutPage() {
  usePageTitle('Store policy')
  return (
    <Prose title="Store policy" lead="How TrustKart works, in plain language.">
      <h2>TrustKart is a virtual store</h2>
      <p>
        TrustKart is a portfolio project that recreates the complete experience of shopping for technology.
        Browsing, carts, checkout, order confirmation and your collection all work like a real store, but
        every purchase is virtual:
      </p>
      <ul>
        <li>
          No real money is ever charged, and TrustKart never asks for card numbers, bank details or billing
          information.
        </li>
        <li>Products are not sold or shipped. No order reaches any manufacturer, retailer or carrier.</li>
        <li>
          Brand and product names belong to their owners. TrustKart is not affiliated with, authorized by or
          endorsed by any of them.
        </li>
      </ul>
      <h2>TrustKart Wallet</h2>
      <p>
        Every shopper starts with a $100,000.00 wallet balance, and you can add more at any time, from $1 to
        $10,000,000 per top-up. The balance is store credit for this demo only: it has no monetary value and
        can’t be withdrawn, transferred or exchanged.
      </p>
      <ul>
        <li>
          <strong>Budget mode</strong> works like a real balance: orders are paid from it and you can run out.
        </li>
        <li>
          <strong>Unlimited mode</strong> lets you buy anything without using your balance.
        </li>
      </ul>
      <h2>Orders, delivery and cancellations</h2>
      <p>
        Delivery is always free and addresses are used only to show on your order, so you can use any address
        you like. You can cancel any order from its order page; the full amount returns to your wallet
        immediately and the items leave <Link to="/collection">your collection</Link>.
      </p>
      <h2>Your data</h2>
      <p>
        See the <Link to="/about/privacy">privacy notice</Link> for exactly what TrustKart stores. Curious how
        it’s built? Read <Link to="/about/project">about this project</Link>.
      </p>
    </Prose>
  )
}
