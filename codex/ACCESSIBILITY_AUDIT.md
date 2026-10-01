# Accessibility audit

54 axe scans covered all 27 route cases at 1440px and 390px in dark theme. Two distinct inline-link targets failed `link-in-text-block` at both widths (four route/width scan violations). Other scanned base states had zero axe violations. Zero automated violations is not a WCAG conformance claim.

## Verified findings

[A11Y-001](OPTIMIZATION_FINDINGS.md#a11y-001): “Change” on rankings and “store policy” on receipts need a persistent non-color cue. This is consistent with [W3C guidance on use of color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html). Keep the palette and add a coherent inline-link treatment.

[A11Y-002](OPTIMIZATION_FINDINGS.md#a11y-002): leaderboard controls expose tab roles but ArrowRight does not move focus/selection. Tab+Space still works. Implement a complete keyboard model consistent with the [W3C tabs pattern](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/), rather than claiming the board is wholly inaccessible.

[A11Y-003](OPTIMIZATION_FINDINGS.md#a11y-003): recent-search remove buttons are tabIndex=-1 and pointer-only with no input keyboard equivalent. Preserve the current functioning combobox selection flow while adding accessible history management.

UX-001/002 also affect accessible operability: clipping/occlusion can hide text and actions even when automated checks pass. UX-003 obscures important monetary values.

## Manual keyboard and semantic checks

| Interaction | Observed result |
|---|---|
| Search typing + ArrowDown | Suggestions appear; active descendant changes |
| Search Enter | Navigates to submitted results |
| Search Escape | Suggestions close |
| Recent search removal | ISSUE A11Y-003 |
| Rankings ArrowRight | ISSUE A11Y-002 |
| Rankings Tab then Space | Changes to All Time |
| Filter dialog Escape | Closes and restores Filters focus |
| Wallet dialog Escape | Settled confirmation closes and restores Add funds focus |
| Notification panel Escape | Closes at tested phone/tablet/desktop widths |
| Filter Shift+Tab during opening | Initial observation landed on BODY; timing/state inconclusive, not promoted to a finding |

Shared skip link, main region, heading presence, labeled controls and product alt text were inspected. Native dialog handling and close controls exist; separate settled captures prevented treating transition frames as persistent viewport/focus bugs. Focus styling, form labels/errors, card image alternatives, table/tab roles and reduced-motion CSS/hero behavior were reviewed in source and representative views. Screenshot/axe review did not identify a broad text-contrast regression.

## Limits and implementation checks

No actual screen-reader session, full light-theme contrast sweep, high-contrast OS setting, exhaustive touch-target measurement, physical keyboard/IME test or all-route zoom campaign was performed. Small icon controls should be evaluated in context; no blanket “all targets are too small” claim is made. Checkout/security keyboard paths were sampled through existing browser flows and source, not fully certified key-by-key.

Re-run axe after each affected UI phase, but also exercise Tab/Shift+Tab/Enter/Space/Escape/arrows manually. Test dialogs after animations settle and with reduced motion. Verify names, state and focus restoration; do not remove useful ARIA or hide controls solely to silence a scanner. Evidence: `accessibility.json`, `interactions.json`, `ui-confirm.json` and screenshots.
