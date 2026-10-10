# Phase 06: Angular FD Frontend — Research

**Date:** 2026-09-22
**Mode:** Existing-codebase research

## Recommendation

Implement the frontend as standalone Angular 18 components and services, reusing the existing `GlassShell`, glass Sass tokens, reactive-form conventions, and lazy route configuration. Do not add a component library, state-management package, direct Supabase client, or backend changes.

## Existing stack and conventions

- `FRONTEND/package.json` contains Angular 18.2, TypeScript 5.5, RxJS 7.8, and no HTTP/auth/UI third-party library.
- Components are standalone and lazy-loaded from `FRONTEND/src/app/app.routes.ts`.
- `AuthService` posts to `/api/auth`, creates `RequestContext`, and currently stores `token` in `localStorage` from `login.ts`.
- `app.config.ts` currently provides router, `HttpClient`, and animations; the auth interceptor and guard should be registered here with functional providers.
- Existing login uses reactive forms, component-owned loading/error state, `GlassShell`, `.glass-card`, `.glass-field`, `.glass-error`, and `.glass-btn`.
- Shared Sass already defines `.glass-card--wide`, `.glass-field-row` with a 480px breakpoint, `.stepper-progress`, `.stepper-actions`, and touch-sized buttons.
- There is no frontend test script in `package.json`; Angular CLI tests are available through `npx ng test --watch=false` and build verification through `npm run build`.

## Backend contract to type exactly

The implemented frontend boundary is `/fd/v1`; snake_case JSON must be mapped by TypeScript interfaces without using `any`:

- `GET /products` → `ProductOfferResponse[]`.
- `POST /accounts` body `{ product_id, principal_amount, tenure_months, currency_code }` → `AccountResponse` with `PENDING` and server-owned rate/dates/maturity.
- `POST /accounts/{id}/fund` body `{ amount, currency_code }`, `Idempotency-Key` → `FundingResponse` with `ACTIVE` and truthful internal-record wording.
- `GET /accounts/{id}` → `AccountResponse`.
- `POST /accounts/{id}/accrual/run` optional `{ business_date }`; `GET /accrual`, `GET /accrual/cumulative` → `AccrualResponse`.
- `POST /accounts/{id}/mature` → `AccrualResponse`.
- `POST /accounts/{id}/close` → `ClosureResponse`.
- `POST/GET /accounts/{id}/premature-closure` and `POST /accounts/{id}/premature-closure/{closureId}/approve` → `PrematureClosureResponse`.
- `GET /accounts/{id}/transactions` optional `from_date`, `to_date`, `txn_type_id` → `HistoryEntry[]`.

The server emits either `GlobalExceptionHandler` envelopes (`status`, `error`, `message`, optional `validationErrors`) or `ApiErrorResponse` (`code`, `message`). UI error mapping must handle 400, 401, 404, 409, 422, and unknown failures without exposing raw credentials or URLs.

## Architecture and risk notes

1. Keep auth state in one service. Preserve the existing login/signup payload contract, add role/session helpers, clear token and role on logout or 401, and redirect protected routes to `/login`.
2. Add a functional bearer interceptor that only adds `Authorization: Bearer <token>` to backend requests; add `Idempotency-Key` only for writes in `FdService` so every write gets a stable retry key.
3. Generate idempotency keys per active submission and reuse the same key when the user retries that submission. On 409, display the backend message and whether the response can be reused; never claim a payment rail was called.
4. Do not call list/search, renew, payout, statement, notice, holder, event, sync, or reference endpoints because they are placeholders or explicitly deferred.
5. Avoid client-side interest/maturity/penalty calculation. Display server-returned values and server error states.

## Verification strategy

- `npm run build` must pass with strict TypeScript and strict Angular templates.
- `npx ng test --watch=false` must pass existing and new focused service/guard/component tests.
- Use `HttpTestingController` tests for authorization headers, idempotency-key reuse, snake_case payloads, error normalization, and route guard decisions.
- Manually exercise the configured backend in a live environment for the complete journey; credentials belong only in the local runtime, never in source, tests, or documentation.

## Explicit exclusions

The separate staff workspace, direct Supabase access, backend/API modifications, current 501 operations, payment rails, and deferred banking features are not frontend tasks for this phase.
