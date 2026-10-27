# Client testing fixes A–H

Scope and acceptance criteria: attached MoneyPal client-testing request, 2026-10-27.
Active implementation is services/, FRONTEND/, and explicitly included service migrations.

Execution order: diagnose D; implement D, E, A, C, B, F, G, H; verify service
contracts and UI checks; build isolated gateway smoke runner; update handoffs.

Baseline commands were started before implementation and compiled the original
sources. Results are retained in identity-baseline.log, fd-baseline.log,
product-baseline.log, frontend-baseline.log. Only disposable Testcontainers are
used. No live stack, existing migration, legacy Java, IDE file, or legacy build
output will be modified. All work remains uncommitted.

Verification: compare all three Maven suites with baseline; Angular build and
focused script checks; six-language coverage for added messages; protected
operation matrix; atomic withdrawal audit; masked staff KYC contracts;
quote/book/maturity parity; isolated gateway smoke. Unexecuted checks remain
UNVERIFIED. Record decisions, defects, and out-of-scope findings in final report.
