import { lazy } from 'react'
import { createBrowserRouter } from 'react-router'
import { HomePage } from '@/features/home/HomePage'
import { NotFoundPage } from '@/features/errors/NotFoundPage'
import { RootLayout } from './RootLayout'
import { RouteError } from './RouteError'

// Everything beyond the home page is split into its own chunk.
const SearchPage = lazy(() => import('@/features/search/SearchPage'))
const ProductPage = lazy(() => import('@/features/product/ProductPage'))
const ComparePage = lazy(() => import('@/features/compare/ComparePage'))
const CartPage = lazy(() => import('@/features/cart/CartPage'))
const CheckoutPage = lazy(() => import('@/features/checkout/CheckoutPage'))
const WalletPage = lazy(() => import('@/features/wallet/WalletPage'))
const CollectionPage = lazy(() => import('@/features/collection/CollectionPage'))
const WishlistPage = lazy(() => import('@/features/wishlist/WishlistPage'))
const SignInPage = lazy(() => import('@/features/auth/SignInPage'))
const SignUpPage = lazy(() => import('@/features/auth/SignUpPage'))
const AccountLayout = lazy(() => import('@/features/account/AccountLayout'))
const AccountOverview = lazy(() => import('@/features/account/AccountOverview'))
const PurchasesPage = lazy(() => import('@/features/account/PurchasesPage'))
const ReceiptPage = lazy(() => import('@/features/account/ReceiptPage'))
const SecurityCenter = lazy(() => import('@/features/account/SecurityCenter'))
const AddressesPage = lazy(() => import('@/features/account/AddressesPage'))
const AboutPage = lazy(() => import('@/features/about/AboutPage'))
const ProjectPage = lazy(() => import('@/features/about/ProjectPage'))
const PrivacyPage = lazy(() => import('@/features/about/PrivacyPage'))
const CreditsPage = lazy(() => import('@/features/about/CreditsPage'))

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'search', element: <SearchPage /> },
      {
        path: 'deals',
        element: <SearchPage preset={{ onSale: 'true', sort: 'discount' }} title="Today’s deals" />,
      },
      { path: 'c/:category', element: <SearchPage /> },
      { path: 'collections/:collection', element: <SearchPage /> },
      { path: 'p/:slug', element: <ProductPage /> },
      { path: 'compare', element: <ComparePage /> },
      { path: 'cart', element: <CartPage /> },
      { path: 'checkout', element: <CheckoutPage /> },
      { path: 'wallet', element: <WalletPage /> },
      { path: 'collection', element: <CollectionPage /> },
      { path: 'wishlist', element: <WishlistPage /> },
      { path: 'signin', element: <SignInPage /> },
      { path: 'signup', element: <SignUpPage /> },
      {
        path: 'account',
        element: <AccountLayout />,
        children: [
          { index: true, element: <AccountOverview /> },
          { path: 'purchases', element: <PurchasesPage /> },
          { path: 'purchases/:id', element: <ReceiptPage /> },
          { path: 'security', element: <SecurityCenter /> },
          { path: 'addresses', element: <AddressesPage /> },
        ],
      },
      { path: 'about', element: <AboutPage /> },
      { path: 'about/project', element: <ProjectPage /> },
      { path: 'about/privacy', element: <PrivacyPage /> },
      { path: 'about/credits', element: <CreditsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
