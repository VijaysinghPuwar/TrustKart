/** ISO 3166-1 alpha-2 codes offered in the (simulation) address form. */
export const COUNTRIES: [code: string, name: string][] = [
  ['US', 'United States'],
  ['CA', 'Canada'],
  ['GB', 'United Kingdom'],
  ['IE', 'Ireland'],
  ['IN', 'India'],
  ['AU', 'Australia'],
  ['NZ', 'New Zealand'],
  ['DE', 'Germany'],
  ['FR', 'France'],
  ['NL', 'Netherlands'],
  ['ES', 'Spain'],
  ['IT', 'Italy'],
  ['SE', 'Sweden'],
  ['CH', 'Switzerland'],
  ['JP', 'Japan'],
  ['KR', 'South Korea'],
  ['SG', 'Singapore'],
  ['AE', 'United Arab Emirates'],
  ['BR', 'Brazil'],
  ['MX', 'Mexico'],
]

export function countryName(code: string | undefined): string {
  return COUNTRIES.find(([c]) => c === code)?.[1] ?? code ?? ''
}
