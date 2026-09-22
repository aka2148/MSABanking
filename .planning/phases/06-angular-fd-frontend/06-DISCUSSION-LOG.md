# Phase 6: Angular FD Frontend - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-09-22
**Phase:** 06-angular-fd-frontend
**Areas discussed:** App structure, Lifecycle interaction, Account presentation, Role behavior

---

## App structure

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard as FD hub | Replace the dashboard stub with an FD overview | |
| Separate FD landing | Keep a separate `/fd` feature area | ✓ |
| Single FD workspace | Redirect dashboard directly to products | |

**User's choice:** Separate FD landing.

| Option | Description | Selected |
|--------|-------------|----------|
| Two clear paths | Browse products and View my deposits | ✓ |
| Products first | Lead directly with offers | |
| Accounts first | Lead with existing accounts | |

**User's choice:** Two clear paths.

---

## Lifecycle interaction

| Option | Description | Selected |
|--------|-------------|----------|
| Guided booking + confirmations | Three-step booking and confirmations | ✓ |
| Single booking form | Compact form and inline actions | |
| Wizard for all actions | Multi-step flow for every write | |

**User's choice:** Guided booking + confirmations.

| Option | Description | Selected |
|--------|-------------|----------|
| Financially final actions | Confirm funding and closure-related actions | ✓ |
| Every write action | Confirm all write operations | |
| Only closure actions | Confirm closure only | |

**User's choice:** Financially final actions: fund, normal close, premature request, and premature approval.

---

## Account presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Single detail hub | One account workspace with status, actions, and history | ✓ |
| Separate pages | Separate status, actions, and history routes | |
| Wizard timeline | Lifecycle steps as the main account UI | |

**User's choice:** Single detail hub.

| Option | Description | Selected |
|--------|-------------|----------|
| Status + next action + timeline | Prioritize current state, next action, and activity | ✓ |
| Full financial summary | Lead with all financial fields | |
| Compact status card | Keep details behind expandable sections | |

**User's choice:** Status + next action + timeline.

---

## Role behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Customer UI first | Self-service customer journeys only | |
| Role-aware shared screens | Shared screens show backend-permitted actions | ✓ |
| Separate staff workspace | Dedicated operations dashboard | |

**User's choice:** Role-aware shared screens. The separate staff workspace was deferred as outside the Phase 6 boundary.

---

## Agent's Discretion

- Exact route names, component/service/model filenames, state approach, history section/tab implementation, token storage mechanism, dialog implementation, loading indicators, timeline visuals, and empty-state copy remain open within the locked decisions.

## Deferred Ideas

- Separate staff/admin operations workspace and cross-customer operations dashboard.
- UI for backend `501 NOT_IMPLEMENTED` operations.
- Direct Supabase database access from Angular.
