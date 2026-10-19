export type FdStatus = 'PENDING' | 'ACTIVE' | 'MATURED' | 'CLOSED' | 'PREMATURELY_CLOSED';

export interface ProductOffer {
  product_id: number;
  product_code: string;
  product_name: string;
  currency_offers: CurrencyOffer[];
  maturity_estimate_basis: string;
}

export interface CurrencyOffer {
  currency_code: string;
  decimal_places: number;
  minimum_amount: number;
  maximum_amount: number;
  minimum_tenure_months: number;
  maximum_tenure_months: number;
  rate_tiers: RateTier[];
}

export interface RateTier {
  minimum_tenure_months: number;
  maximum_tenure_months: number;
  annual_rate: number;
  compounding_frequency: string;
  payout_frequency: string;
}

export interface Journal {
  journal_id: number; operation: string; posted_at: string; savings_account_id: number;
  currency_code: string; total_debit: number; total_credit: number; savings_balance_after: number;
  lines: { account_code: string; debit: number; credit: number }[];
}

export interface SavingsAccount {
  account_id: number;
  account_number: string;
  display_name: string;
  currency_code: string;
  balance: number;
  status: string;
  created_date: string;
}

export interface BookingRequest {
  funding_savings_account_id?: number;
  product_id: number;
  principal_amount: number;
  tenure_months: number;
  currency_code: string;
}

export interface FundingRequest {
  amount: number;
  currency_code: string;
}

export interface PrematureClosureRequest {
  reason?: string;
}

export interface AccountResponse {
  funding_savings_account_id?: number | null;
  account_id: number;
  account_number: string;
  product_id: number;
  product_code: string;
  currency_code: string;
  principal_amount: number;
  tenure_months: number;
  annual_rate: number;
  compounding_frequency: string;
  payout_frequency: string;
  open_date: string;
  maturity_date: string;
  maturity_amount: number;
  policy_version: string;
  status: FdStatus;
  funding_recorded: boolean;
}

export interface FdAccountSummary {
  account_id: number;
  account_number: string;
  product_code: string;
  product_name: string;
  currency_code: string;
  principal_amount: number;
  tenure_months: number;
  annual_rate: number | null;
  open_date: string | null;
  maturity_date: string | null;
  maturity_amount: number | null;
  status: FdStatus;
  funding_recorded: boolean;
}

export interface FdAccountListResponse {
  data: FdAccountSummary[];
  pagination: {
    page: number;
    page_size: number;
    total_records: number;
  };
}

export interface FundingResponse extends AccountResponse {
  funding_recorded: true;
  funding_description: string;
}

export interface AccrualResponse {
  account_id: number;
  account_number: string;
  accrual_date: string;
  accrued_amount: number;
  cumulative_accrued: number;
  currency_code: string;
  maturity_amount: number;
  policy_version: string;
  status: FdStatus;
}

export interface ClosureResponse {
  account_id: number;
  account_number: string;
  status: 'CLOSED';
  closed_date: string;
  settlement_amount: number;
  currency_code: string;
  policy_version: string;
  remark: string;
}

export interface PrematureClosureResponse {
  closure_id: number;
  account_id: number;
  status: 'PENDING' | 'APPROVED';
  requested_date: string;
  penalty_points: number;
  penalized_rate: number;
  penalty_amount: number;
  net_payable_amount: number;
  currency_code: string;
  approved_by: number | null;
}

export interface HistoryEntry {
  entry_type: string;
  entry_date: string;
  amount: number;
  currency_code: string;
  running_balance: number | null;
  source: 'TRANSACTION' | 'ACCRUAL';
  description: string;
}

export interface ApiErrorEnvelope {
  status?: number;
  error?: string;
  message?: string;
  validationErrors?: Record<string, string>;
  code?: string;
}

export interface ApiErrorResponse {
  status: number;
  message: string;
  validationFields: Record<string, string>;
  replayHint: string | null;
}
