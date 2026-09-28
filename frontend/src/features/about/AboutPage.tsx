import { Link } from 'react-router'
import { usePageTitle } from '@/lib/usePageTitle'
import { Prose } from './Prose'

export default function AboutPage() {
  usePageTitle('How TrustKart works')
  return (
    <Prose
      title="How TrustKart works"
      lead="A realistic technology store where every purchase is virtual. Browse, dream, check out, collect. Spend nothing."
    >
      <h2>What’s real and what isn’t</h2>
      <ul>
        <li>The catalog describes real technology products with realistic prices and specifications.</li>
        <li>
          TrustKart does not sell them. Nothing is charged, nothing ships, and no order reaches any
          manufacturer or retailer.
        </li>
        <li>
          Brand and product names belong to their owners. TrustKart is not affiliated with, authorized by or
          endorsed by any of them.
        </li>
      </ul>
      <h2>Your TrustKart Wallet</h2>
      <p>
        Every shopper starts with $100,000.00 in virtual funds. You can add more at any time, from $1 to
        $10,000,000 per top-up. Virtual funds have no monetary value and can’t be withdrawn, transferred or
        exchanged.
      </p>
      <ul>
        <li>
          <strong>Budget mode</strong> works like a real balance: purchases draw it down and you can run out.
        </li>
        <li>
          <strong>Unlimited mode</strong> lets you buy anything; your balance stays put.
        </li>
      </ul>
      <h2>No financial information, ever</h2>
      <p>
        TrustKart never asks for card numbers, bank details, billing addresses or any financial identity.
        Checkout “delivery” is a preset such as Home or Dream setup, or a fictional place you name yourself.
      </p>
      <h2>Your collection</h2>
      <p>
        Everything you buy virtually joins <Link to="/collection">My collection</Link>, with its virtual value
        and a few achievements. You can undo any purchase, which returns the virtual funds and removes the
        items.
      </p>
      <p>
        Curious how it’s built? Read <Link to="/about/project">about this project</Link>.
      </p>
    </Prose>
  )
}
