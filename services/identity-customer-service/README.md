# Identity and Customer Service

Identity and Customer owns `moneypal_customer`. In local demo Compose, it connects to the dedicated MySQL container and publishes customer snapshots through Kafka. Flyway applies the schema migrations at startup, currently through V16.

## Authentication and password security

Public routes include `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/login/mfa/verify`, and the password recovery routes below. Authenticated routes include refresh, logout, profile, `POST /auth/password/change`, and TOTP enrollment/enable/disable. Login accepts either username or a unique primary 10-digit customer phone number. Passwords are BCrypt hashes; failed login attempts are locked for 15 minutes after five failures. Access tokens carry a server-checked token version, so password changes, logout, status changes, and role changes revoke existing tokens.

MFA uses RFC 6238 TOTP. The authenticator secret is encrypted at rest using `MFA_ENCRYPTION_KEY`; the last accepted time step is stored to prevent code replay. Enrollment returns a provisioning URI once to the authenticated caller. The current Angular login page does not yet collect an MFA code, so MFA sign-in is available through the API and requires a frontend follow-up to use from the UI.

### Forgot password

The Angular `/forgot-password` page and API use a three-step email-code flow. The older link-based `/auth/password/forgot` and `/auth/password/reset` handlers have been removed.

1. `POST /api/v1/auth/password/forgot` accepts `{"identifier":"username-or-primary-phone"}` and returns HTTP 202 with the same generic message for existing, missing, ineligible, and rate-limited accounts. Only active customer accounts with a verified primary email receive mail. Responses do not disclose the registered email address.
2. `POST /api/v1/auth/password/verify` accepts `{"identifier":"username-or-primary-phone","code":"123456"}`. A valid code returns `{"resetToken":"...","expiresInSeconds":600}`. Invalid, expired, exhausted, and ineligible codes return the same HTTP 400 message.
3. `POST /api/v1/auth/password/reset` accepts `{"resetToken":"...","newPassword":"..."}`. It applies the normal password-strength and last-five-password checks, consumes the token once, updates password history and lock state, increments the session token version, and returns a new customer session using the existing JWT shape. A reused or expired token returns HTTP 400.

Codes are six digits, expire after 10 minutes, and allow five verification attempts. Request resend is limited to once per minute and five per hour per account; the gateway also applies an IP-based limit of 10 requests/minute with a burst of five. Only a domain-separated HMAC of each code and a SHA-256 hash of each reset token are stored. A new request invalidates prior codes. Expired OTP rows are purged during new requests after 24 hours. Password reset does not read, update, or clear customer MPIN data.

Mail uses the configured Brevo-compatible API (`api-key` header and `/v3/smtp/email` JSON payload) on a bounded asynchronous queue after the database transaction commits. Provider bodies, codes, email addresses, API keys, and reset tokens are not written to application logs. Delivery failure burns the new code. The changed-password notice is best-effort and contains no reset material.

Set `PASSWORD_RESET_ENABLED=true`, `PASSWORD_RESET_API_KEY`, `PASSWORD_RESET_API_URL`, and an accepted `PASSWORD_RESET_FROM` value in the local ignored `.env`. The checked-in example intentionally has an empty API key; without a key or with the feature disabled, the endpoints return HTTP 503. `PASSWORD_RESET_URL` is retained in example/Compose wiring only as a deprecated variable and is ignored by the service because reset links are no longer used.

To exercise the flow without a provider, run `mvn -f services/identity-customer-service/pom.xml "-Dtest=PasswordResetMailSenderTest,AuthDtoRedactionTest,IdentityCustomerMigrationTest#passwordReset*" test`. The integration test starts an in-process loopback HTTP mail sink and a disposable Testcontainers MySQL database; it does not use the configured Compose database. It covers request, verify, reset, delivery failure, throttling, token single-use, and log-redaction behavior.

## Admin APIs

`ROLE_BANK_OFFICER` and `ROLE_ADMIN` can read customers, users, roles, branches, employees, and customer classification types. `ROLE_ADMIN` can change user status and role assignments, and create/update roles, branches, employees, and classification types. The service prevents suspending or removing the final active administrator. Sensitive credential fields are never returned by these APIs.

## Local connection

The checked-in Compose stack currently points to its `customer-db` MySQL container. Running the SQL in MySQL Workbench created a separate database instance; the app will not use it unless `CUSTOMER_DB_URL`, `CUSTOMER_DB_USERNAME`, and `CUSTOMER_DB_PASSWORD` are changed. Keep secrets in `.env`, not tracked files.
