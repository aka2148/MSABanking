# Banking System - Frontend

Verified: `npm install && ng build` completes clean (1.45 MB initial bundle, all
routes lazy-loaded). If it doesn't build on your machine, the difference is
your Node/npm version - this was tested against Angular 18.

## Run it

```
npm install
npm start
```
Open http://localhost:4200 - lands on `/login`.

## What's in here

```
src/app/
├── core/
│   ├── models/auth.models.ts     # LoginRequest/Response, SignupRequest, RequestContext, KycDocument
│   └── services/auth.service.ts  # login(), signup(), buildContext()
├── features/
│   ├── auth/login/               # single-step, per your "keep it simple" brief
│   ├── auth/signup/               # 3-step glide stepper
│   └── dashboard/                 # stub landing page, delete once real dashboard exists
└── shared/
    ├── components/glass-shell/    # bg photo + click-glide + scroll-parallax wrapper
    └── styles/                    # _tokens.scss (palette/type), _glass.scss (component classes)
```

## Schema gap analysis - what we already handle vs. what's still open

Checked against the customer module team's list of missing tables:

**Already aligned, no frontend change needed:**
- `audit_log` / `created_by` / `updated_by` - `RequestContext` in
  `auth.models.ts` (channel, localTimestamp, requestId) is built specifically
  to feed this. `AuthService.buildContext()` generates it on every call -
  reuse it, don't rebuild it, in FD/transactions/forex services.
- `currency` reference table - matches `PreferredCurrency` union type already
  in the models file.
- `branch`, `account_number_sequence`, `fd_charges_applied` - these are
  FD-account-level concerns, out of scope until that module is built.

**Added in this pass:**
- `kyc_documents` - signup Step 2 now has a file input (`onFileSelected()` in
  `signup.ts`). **Not yet wired to a real endpoint** - `signup()` currently
  posts JSON only. Once backend exposes a `kyc_documents` upload route,
  this needs a separate multipart request, probably fired right after signup
  succeeds using the returned `customerIdentifier`.
- `user_roles`/`roles` - `LoginResponse` now has an optional `role` field.
  Not used yet (no route guards exist), but the model won't need touching
  again once guards get built.

**Still needs a conversation with the backend team, not a frontend fix:**
- `user_customer_map` - nothing to build here on my end, but confirm the
  actual FK/linking field name before wiring `signup()`'s response handling
  to anything beyond a redirect.

## Known gaps / next steps

1. Signup's file upload is UI-only - see the note above.
<img width="1917" height="862" alt="image" src="https://github.com/user-attachments/assets/7555183b-3b15-4b7b-a78e-24938c27c49d" />
<img width="1917" height="871" alt="image" src="https://github.com/user-attachments/assets/0a145472-0b3c-4bdf-b60a-df9ff6ca5763" />

2. No route guard yet - `/dashboard` is reachable without logging in. Add
   once `role` handling is real.
3. `AuthService.baseUrl` is hardcoded to `localhost:8080/api/auth` - move to
   an `environment.ts` once you're deploying anywhere beyond your own machine.
4. Login/signup are the only screens using the glass shell so far - reuse
   `<app-glass-shell>` for every other module for visual consistency, per the
   "full glass everywhere" call.

## Visuals 
<img width="1910" height="965" alt="image" src="https://github.com/user-attachments/assets/09cf93d8-1570-453c-ac31-3a6c6d0e4592" />
<img width="1747" height="791" alt="image" src="https://github.com/user-attachments/assets/208a2553-aa5e-4cb1-a5b8-bdbf16498a13" />


