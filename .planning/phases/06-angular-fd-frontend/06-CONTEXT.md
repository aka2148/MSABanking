# Phase 6: Angular FD Frontend - Context

**Gathered:** 2026-09-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the Angular frontend for the supported FD lifecycle using the existing authenticated application and visual language. The phase covers a separate FD landing page, eligible product browsing, guided booking, funding, account status, accrual, maturity, normal closure, premature closure, and unified history. It also covers the frontend authentication plumbing needed to call the existing `/fd/v1` API safely. A separate staff operations workspace, placeholder `501 NOT_IMPLEMENTED` operations, backend changes, real payment rails, and broader banking features are outside this phase.

</domain>

<decisions>
## Implementation Decisions

### Application structure
- **D-01:** Use a separate authenticated FD feature area rather than replacing the dashboard stub with the FD hub. Add an FD landing route and focused `/fd/...` child routes behind the same authentication guard.
- **D-02:** The FD landing page presents two equally clear entry paths: `Browse products` and `View my deposits`. It may include a compact lifecycle explanation, but it must not fabricate account totals or financial data that the API did not return.
- **D-03:** The existing dashboard route remains a compatibility entry point; the FD feature owns its own navigation and route structure. Exact dashboard-link treatment is an implementation detail for planning.

### Lifecycle interaction
- **D-04:** Booking uses a guided three-step flow: offer confirmation, amount/term/currency input, and server-backed review/confirmation. The review displays server-returned rate, dates, maturity estimate, and policy information rather than treating client calculations as authoritative.
- **D-05:** Use explicit final confirmation for financially final actions: funding, normal closure, premature-closure request, and premature-closure approval. Read operations and accrual/maturity processing can use clearly labeled action buttons with result feedback rather than an additional confirmation dialog.
- **D-06:** Lifecycle action controls are status-aware and show only valid next actions for the current account state. Backend 409/422 responses remain the authority and must be rendered when state changes or retries make the UI stale.
- **D-07:** All frontend writes send an `Idempotency-Key` generated client-side and retain the key for the active submission/retry attempt. A 409 replay/conflict must explain whether the existing operation can be safely reused; the UI must never imply external payment settlement.

### Account presentation
- **D-08:** Use one account detail hub at `/fd/accounts/:id`, not separate status/action/history pages. This keeps the complete academic lifecycle understandable in one place.
- **D-09:** The account hub leads with current status, the next valid action or actions, and a chronological activity timeline. Locked terms and financial details remain visible below or alongside the status/action area.
- **D-10:** The timeline uses the backend unified history response and distinguishes funding, accrual, maturity, closure, settlement, and penalty entries. Filters may use the existing server-side date/type query parameters; no client-side financial reconstruction is authoritative.
- **D-11:** Loading, empty, error, success, and disabled-action states are designed for the hub and each supported action. A pending account must make the funding next step obvious; closed accounts must show the settlement/history outcome without offering invalid actions.

### Role behavior
- **D-12:** Use role-aware shared screens, not a separate staff workspace. Customers receive the self-service journey; `BANK_OFFICER` and `ADMIN` may see and use actions already permitted by the backend role rules through the same product/account components.
- **D-13:** The frontend must not infer authorization from route visibility alone. Backend responses remain authoritative; forbidden or ownership-protected resources are handled as API errors. No caller-controlled customer/holder identifier is added to the UI contract.
- **D-14:** A separate staff/admin operations dashboard is deferred. Staff-specific navigation, search, portfolio management, and cross-customer workflows are not part of Phase 6.

### Visual language and responsive behavior
- **D-15:** Reuse `GlassShell`, `.glass-card`/`.glass-card--wide`, `.glass-field`, `.glass-error`, `.glass-btn`, `.glass-link`, `.stepper-progress`, `.stepper-actions`, `app-glass-select`, existing tokens, and the Outfit/Inter/IBM Plex Mono typography. Do not introduce a second design system.
- **D-16:** Match the login visual language: world-map background, dark translucent scrim, gold primary actions, white glass-card content, and existing ink/gold/mint/rose semantic colors. Exact spacing and component decomposition remain planner discretion when they preserve this language.
- **D-17:** Product grids, account timelines, tables, and action groups must collapse into usable single-column mobile layouts using the established 480px field breakpoint and appropriate touch-sized controls.

### Authentication and API boundary
- **D-18:** Add the minimum frontend auth plumbing required by the existing backend: session token/role persistence, bearer authorization for FD requests, protected dashboard/FD routes, and logout/expired-token handling. Preserve the existing login/signup contract and do not rewrite backend authentication.
- **D-19:** Create typed FD service/model boundaries for the implemented `/fd/v1` operations only: products, booking, funding, status, accrual reads/run, maturity, normal close, premature-close request/list/approval, and transaction history.
- **D-20:** Handle both backend error shapes: real errors from `GlobalExceptionHandler` (`status`, `error`, `message`, optional `validationErrors`) and the existing `ApiErrorResponse` placeholder shape (`code`, `message`). Map 400, 401, 404, 409, 422, and unexpected failures into safe in-context UI messages.
- **D-21:** Do not expose or build controls for current `501 NOT_IMPLEMENTED` operations such as account search/list, renew, payouts, statements, notices, holders, events, and reference/sync endpoints.

### the agent's Discretion
- Exact Angular route names below `/fd`, component/service/model filenames, state-management approach, and whether history is a section or tab within the account hub.
- Exact token storage mechanism, provided credentials are not logged, URLs remain clean, logout clears session state, and route/API protection is reliable.
- Exact confirmation dialog implementation, loading indicators, timeline visuals, and empty-state copy, provided D-04 through D-17 remain true.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase and project scope
- `.planning/PROJECT.md` - Project vision, academic constraints, compatibility rules, and frontend boundary.
- `.planning/REQUIREMENTS.md` - EXT-04 frontend requirement and explicit v2/out-of-scope boundaries.
- `.planning/ROADMAP.md` - Phase 6 goal, dependencies, and success criteria.
- `.planning/STATE.md` - Locked FD money, lifecycle, ownership, idempotency, and truthful-response decisions.
- `.planning/phases/02-product-booking-and-funding/02-CONTEXT.md` - API-first, server-authoritative booking/funding, truthful internal-record wording, and no caller-controlled customer identity.
- `.planning/phases/04-closure-and-demonstrable-api-workflow/04-CONTEXT.md` - Closure, premature-closure, penalty, history, and demo decisions.
- `.planning/phases/05-protection-tests-and-regression-gate/05-CONTEXT.md` - Idempotency, ownership, error, role, and regression decisions the frontend must honor.

### Existing Angular visual and application patterns
- `FRONTEND/src/app/features/auth/login/login.html` - Existing glass form composition, error banner, loading button, and route-link pattern.
- `FRONTEND/src/app/features/auth/login/login.ts` - Existing reactive-form submission and backend error handling pattern.
- `FRONTEND/src/app/features/dashboard/dashboard-stub.ts` - Current dashboard compatibility entry point to replace or preserve through routing.
- `FRONTEND/src/app/app.routes.ts` - Existing standalone lazy route surface.
- `FRONTEND/src/app/app.config.ts` - Existing router, HTTP, and animation providers.
- `FRONTEND/src/app/shared/components/glass-shell/glass-shell.ts` - Reusable authenticated page shell and responsive alignment options.
- `FRONTEND/src/app/shared/components/glass-shell/glass-shell.html` - Glass shell markup/brand composition.
- `FRONTEND/src/app/shared/components/glass-shell/glass-shell.scss` - Shell layout and responsive behavior.
- `FRONTEND/src/app/shared/components/glass-select/glass-select.ts` - Reusable ControlValueAccessor select component.
- `FRONTEND/src/app/shared/styles/_tokens.scss` - Color, typography, spacing, and radius tokens.
- `FRONTEND/src/app/shared/styles/_glass.scss` - Glass cards, fields, buttons, errors, stepper, and responsive shared styles.
- `FRONTEND/src/styles.scss` - Global style entry point.
- `FRONTEND/src/app/core/services/auth.service.ts` - Existing auth API, request context generation, and token persistence behavior.
- `FRONTEND/src/app/core/models/auth.models.ts` - Existing login/signup response and auth model contract.

### FD REST and backend contracts
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` - `/fd/v1` paths, snake_case payloads, response schemas, and placeholder operations.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` - Implemented FD endpoint paths, roles, request handling, and idempotency header forwarding.
- `BACKEND/src/main/java/com/bank/fd/api/dto/BookingRequest.java` - Booking request shape and validation.
- `BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java` - Account/status/locked-term response shape.
- `BACKEND/src/main/java/com/bank/fd/api/dto/FundingRequest.java` - Exact-principal funding request shape.
- `BACKEND/src/main/java/com/bank/fd/api/dto/FundingResponse.java` - Funding response and truthful internal-record fields.
- `BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java` - Accrual and maturity response shape.
- `BACKEND/src/main/java/com/bank/fd/api/dto/ClosureResponse.java` - Normal closure/settlement response shape.
- `BACKEND/src/main/java/com/bank/fd/api/dto/PrematureClosureResponse.java` - Premature request/approval and penalty response shape.
- `BACKEND/src/main/java/com/bank/fd/api/dto/HistoryEntry.java` - Unified transaction/accrual timeline entry shape.
- `BACKEND/src/main/java/com/bank/fd/api/dto/ProductOfferResponse.java` - Eligible product offer fields.
- `BACKEND/src/main/java/com/bank/fd/api/dto/ApiErrorResponse.java` - Placeholder/error response shape.
- `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java` - Real error envelope and HTTP status mapping.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlassShell` provides the existing world-map background, brand treatment, desktop alignment, and mobile behavior.
- Shared glass Sass already provides cards, fields, buttons, error banners, stepper progress, and responsive two-column fields.
- `GlassSelect` is a standalone ControlValueAccessor suitable for product, currency, and term choices.
- `AuthService.buildContext()` already creates request IDs and web/mobile channel metadata for write operations.
- Angular uses standalone components and lazy `loadComponent` routes; feature code belongs under `FRONTEND/src/app/features/`, shared API/auth code under `core/`, and reusable controls under `shared/`.

### Established Patterns
- Reactive forms validate before side effects; components own loading/error state and subscribe to service observables.
- Login already stores a returned token in `localStorage`, but there is no interceptor or route guard yet; Phase 6 must complete the minimum secure session path without breaking login.
- No frontend FD state-management library, HTTP interceptor, or route guard exists. Keep the first implementation simple and aligned with existing service/component patterns.
- Current dashboard is an inline placeholder and is not a visual system to extend. The FD feature should use the established glass system instead.

### Integration Points
- Add lazy FD routes to `FRONTEND/src/app/app.routes.ts`, preserving landing, login, signup, and dashboard compatibility.
- Register HTTP auth/interceptor providers through `FRONTEND/src/app/app.config.ts` and keep API base URLs configurable rather than embedding Supabase secrets.
- Add typed FD API service/models under `FRONTEND/src/app/core/` and feature screens under `FRONTEND/src/app/features/fd/`.
- Connect the UI to the existing `/fd/v1` backend and configured Supabase-backed environment; no direct frontend database access is allowed.

</code_context>

<specifics>
## Specific Ideas

- The FD landing page should make two paths immediately clear: `Browse products` and `View my deposits`.
- The booking flow should read as a guided three-step demonstration rather than a dense banking form.
- The account detail hub should make the current status and next valid action obvious before showing the timeline.
- Financially final actions should ask for explicit confirmation; normal reads and processing actions should stay direct and explain their result.
- Staff/admin should share the same screens where backend permissions allow it, but a separate staff workspace is deferred.
- Manual end-to-end verification should use the configured Supabase-backed backend without putting credentials in source, tests, or documentation.

</specifics>

<deferred>
## Deferred Ideas

- Separate staff/admin operations workspace, portfolio search, and cross-customer operations dashboard - future phase.
- UI for backend placeholder operations: renewals, payouts, statements, notices, holders, events, sync, and reference-data administration - future phases.
- Direct Supabase client/database access from Angular - not part of the architecture; use the backend API.

</deferred>

---

*Phase: 06-angular-fd-frontend*
*Context gathered: 2026-09-22*
