# Identity and Customer Service

Identity and Customer owns `moneypal_customer`. In local demo Compose, it connects to the dedicated MySQL container and publishes customer snapshots through Kafka. Flyway applies the initial schema and the additive `V2__identity_security_admin.sql` migration at startup.

## Authentication and password security

Public routes include `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/login/mfa/verify`, `/auth/password/forgot`, and `/auth/password/reset`. Authenticated routes include refresh, logout, profile, password change, and TOTP enrollment/enable/disable. Passwords are BCrypt hashes; failed login attempts are locked for 15 minutes after five failures. Access tokens carry a server-checked token version, so password changes, logout, status changes, and role changes revoke existing tokens.

MFA uses RFC 6238 TOTP. The authenticator secret is encrypted at rest using `MFA_ENCRYPTION_KEY`; the last accepted time step is stored to prevent code replay. Enrollment returns a provisioning URI once to the authenticated caller. The current Angular login page does not yet collect an MFA code, so MFA sign-in is available through the API and requires a frontend follow-up to use from the UI.

Password reset creates a single-use, 30-minute token and sends it through the configured Resend-compatible email API. Delivery is disabled by default. To enable it, set `PASSWORD_RESET_ENABLED=true`, `PASSWORD_RESET_API_KEY`, and a sender accepted by the provider in the local ignored `.env`. Set `PASSWORD_RESET_URL` to the UI reset page. Without delivery configuration, the reset endpoints return HTTP 503 rather than creating an unusable token.

## Admin APIs

`ROLE_BANK_OFFICER` and `ROLE_ADMIN` can read customers, users, roles, branches, employees, and customer classification types. `ROLE_ADMIN` can change user status and role assignments, and create/update roles, branches, employees, and classification types. The service prevents suspending or removing the final active administrator. Sensitive credential fields are never returned by these APIs.

## Local connection

The checked-in Compose stack currently points to its `customer-db` MySQL container. Running the SQL in MySQL Workbench created a separate database instance; the app will not use it unless `CUSTOMER_DB_URL`, `CUSTOMER_DB_USERNAME`, and `CUSTOMER_DB_PASSWORD` are changed. Keep secrets in `.env`, not tracked files.