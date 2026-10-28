-- Published FD products use the standard 1.00 percentage-point reduction.
UPDATE fd_product_currency_ref
SET premature_penalty_points=1.0000
WHERE premature_penalty_points IS NULL;

-- Preserve valid booked values. Fill only missing/null snapshot keys, and only
-- when the account's originally booked annual rate is available.
UPDATE fd_accounts account_row
SET fa_terms_snapshot=JSON_SET(
    COALESCE(account_row.fa_terms_snapshot,JSON_OBJECT()),
    '$.annualRate',
    COALESCE(
        CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(account_row.fa_terms_snapshot,'$.annualRate')),'null') AS DECIMAL(9,4)),
        account_row.fa_interest_rate
    ),
    '$.prematurePenaltyPoints',
    COALESCE(
        CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(account_row.fa_terms_snapshot,'$.prematurePenaltyPoints')),'null') AS DECIMAL(9,4)),
        1.0000
    )
)
WHERE account_row.fa_status='ACTIVE'
  AND (
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(account_row.fa_terms_snapshot,'$.prematurePenaltyPoints')),'null') IS NULL
      OR (
          account_row.fa_interest_rate IS NOT NULL
          AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(account_row.fa_terms_snapshot,'$.annualRate')),'null') IS NULL
      )
  );
