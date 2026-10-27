# Phase 7: Customer Email MFA and Session Lifetimes

## Goal

Customer password sign-in requires an email OTP delivered by the existing identity-service email provider. Admin/staff password sign-in remains direct. Authenticated sessions enforce role-specific idle and absolute limits in the identity service and Angular app.

## Decisions

- Reuse the identity service's existing Brevo transactional email configuration and verified primary customer email.
- Customers: 5-minute idle limit, warning at 4 minutes with a continue action, 30-minute absolute lifetime.
- Admin/staff: 15-minute idle limit, warning at 14 minutes with a continue action, 8-hour absolute lifetime.
- Session expiry is enforced in the identity database and JWT claims; UI timers provide the warning and redirect.
- Signup email verification remains separate from sign-in MFA.

## Work

1. [x] Add a customer-only email OTP challenge and verification step in identity-service login; preserve direct password login for non-customers.
2. [x] Add database-backed session records and enforce inactivity, revocation, and absolute expiry in the identity JWT filter used by downstream services.
3. [x] Add the Angular OTP screen, global inactivity prompt, heartbeat, continue, and sign-out behavior.
4. [x] Inspect API contracts and compile identity-service main sources without reading `.env` values.

## Verification

- `mvn compile` succeeded for the identity-customer-service main sources.
- `mvn -DskipTests package` compiled main and test sources but could not complete packaging because Maven Surefire artifacts were unavailable from the repository. Tests were not run.
- Angular dependencies are not installed in `FRONTEND/node_modules`, so the frontend build was not run.
