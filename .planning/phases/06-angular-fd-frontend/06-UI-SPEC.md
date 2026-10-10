# Phase 06: Angular FD Frontend — UI Design Contract

## Visual contract

- Reuse `GlassShell` with `/world-map.png`, dark translucent scrim, white glass cards, gold primary actions, and existing ink/gold/mint/rose semantic colors.
- Use `var(--font-display)` / Outfit for headings, `var(--font-body)` / Inter for UI copy, and `var(--font-mono)` / IBM Plex Mono for account numbers and compact financial metadata.
- Use `.glass-card`, `.glass-card--wide`, `.glass-field`, `.glass-error`, `.glass-btn`, `.glass-link`, `.stepper-progress`, `.stepper-actions`, and `app-glass-select`; add only FD-specific layout classes in feature-local SCSS.

## Information architecture

- `/fd` is the authenticated hub with two equal entry paths: `Browse products` and `View my deposits`.
- `/fd/book/:productId` is a three-step flow: offer confirmation → amount/term/currency input → server-backed review/confirmation.
- `/fd/accounts/:id` is the single account detail hub. It leads with status, valid next action(s), and the chronological unified history timeline; locked terms and financial details follow.
- Preserve `/dashboard` as a compatibility route, but route its useful entry action into the FD area rather than exposing an unrelated new visual system.

## Interaction contract

- Financially final actions (fund, normal close, premature request, premature approval) require explicit confirmation before the write.
- Accrual and maturity processing use clearly labelled action buttons with loading and result feedback, not confirmation dialogs.
- Action controls are derived from the current status. Pending exposes funding; active exposes accrual/maturity and premature request; matured exposes close and premature request where applicable; closed exposes history only.
- Every loading, empty, success, error, and disabled state is visible in the affected screen. Backend 409/422 and ownership/auth errors remain visible as contextual messages.
- Internal funding and closure copy must say that the record is internal academic recordkeeping and must not imply external payment settlement.

## Responsive contract

- Product grids, action groups, details, and timeline rows collapse to one column at the established 480px breakpoint.
- Buttons and controls remain touch-sized; no horizontal scrolling is required for the booking flow or account hub.
- Keep contrast and focus styles from the existing glass controls; do not replace shared tokens.
