import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { get, queryString } from '@/lib/api'
import type {
  Category,
  Compare,
  Facets,
  Home,
  Page,
  ProductCard,
  ProductDetail,
  SearchResult,
  Suggestions,
} from '@/lib/types'
import { qk } from './keys'

declare global {
  interface Window {
    /** Home data requested early by /theme-init.js; consumed by the first home query. */
    __tkHome?: Promise<Home | null>
  }
}

export function useHome() {
  return useQuery({
    queryKey: qk.home,
    queryFn: async ({ signal }) => {
      const early = window.__tkHome
      window.__tkHome = undefined
      const data = early ? await early : null
      return data ?? get<Home>('/catalog/home', signal)
    },
    staleTime: 60_000,
  })
}

export function useCategories() {
  return useQuery({
    queryKey: qk.categories,
    queryFn: ({ signal }) => get<Category[]>('/catalog/categories', signal),
    staleTime: 5 * 60_000,
  })
}

export function useProduct(slug: string) {
  return useQuery({
    queryKey: qk.product(slug),
    queryFn: ({ signal }) => get<ProductDetail>(`/catalog/products/${encodeURIComponent(slug)}`, signal),
  })
}

export function useFacets(category: string | undefined) {
  return useQuery({
    queryKey: qk.facets(category ?? ''),
    queryFn: ({ signal }) =>
      get<Facets>(`/catalog/categories/${encodeURIComponent(category ?? '')}/facets`, signal),
    enabled: Boolean(category),
    staleTime: 5 * 60_000,
  })
}

/** Search with interpretation. `qs` is the URL query string, so the URL is the single source of truth. */
export function useSearch(qs: string) {
  return useQuery({
    queryKey: qk.search(qs),
    queryFn: ({ signal }) => get<SearchResult>(`/search${qs}`, signal),
    placeholderData: keepPreviousData,
  })
}

/** Suggestions start at two characters; an empty box shows recent and example searches without a request. */
export const SUGGEST_MIN_LENGTH = 2

export function useSuggestions(q: string) {
  return useQuery({
    queryKey: qk.suggest(q),
    queryFn: ({ signal }) => get<Suggestions>(`/search/suggest${queryString({ q })}`, signal),
    enabled: q.length >= SUGGEST_MIN_LENGTH,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  })
}

export function useLookup(ids: number[]) {
  return useQuery({
    queryKey: qk.lookup(ids),
    queryFn: ({ signal }) =>
      get<ProductCard[]>(`/catalog/products/lookup${queryString({ ids: ids.map(String) })}`, signal),
    enabled: ids.length > 0,
  })
}

export function useCompare(slugs: string[]) {
  return useQuery({
    queryKey: qk.compare(slugs),
    queryFn: ({ signal }) => get<Compare>(`/catalog/compare${queryString({ slugs })}`, signal),
    enabled: slugs.length >= 2,
  })
}

export function useCollectionShelf(tag: string) {
  return useQuery({
    queryKey: ['collection-shelf', tag],
    queryFn: ({ signal }) =>
      get<Page<ProductCard>>(`/catalog/products${queryString({ collection: tag, size: 48 })}`, signal),
  })
}
