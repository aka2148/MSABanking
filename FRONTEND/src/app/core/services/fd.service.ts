import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Inject, Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, catchError, map, retry, switchMap, tap, throwError, timer } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';
import {
  AccountResponse,
  SavingsAccount,
  CurrentAccount,
  DepositProductOffer,
  Journal,
  AccrualResponse,
  ApiErrorEnvelope,
  ApiErrorResponse,
  BookingRequest,
  ClosureResponse,
  FundingRequest,
  FundingResponse,
  HistoryEntry,
  PrematureClosureRequest,
  PrematureClosureResponse,
  ProductOffer,
  FdAccountSummary,
  FdAccountListResponse,
  FdStatus,
} from '../models/fd.models';

export const FD_API_BASE_URL = new InjectionToken<string>('FD_API_BASE_URL', {
  providedIn: 'root',
  factory: () => apiUrl(inject(API_BASE_URL), '/api/v1'),
});

export interface TransactionQuery {
  from_date?: string;
  to_date?: string;
  txn_type_id?: number;
}

export interface FdEstimate {
  productId:number;currencyCode:string;currencyScale:number;principal:number;annualRate:number;
  compoundingBasis:string;payoutFrequency:string;openDate:string;maturityDate:string;
  estimatedInterest:number;estimatedMaturityAmount:number;
}

export interface DepositStatementPage {
  content: Array<{id:number;reference:string;entryType:string;amount:number;balanceAfter:number;remarks:string|null;postedAt:string}>;
  page:number;size:number;totalElements:number;totalPages:number;openingBalance:number;closingBalance:number;
}

export function normalizeApiError(error: HttpErrorResponse): ApiErrorResponse {
  const payload = isApiErrorEnvelope(error.error) ? error.error : {};
  const status = error.status || 500;
  const message = payload.message ?? payload.error ?? payload.code ?? safeMessage(status);
  return {
    status,
    message,
    validationFields: payload.validationErrors ?? {},
    replayHint: status === 409
      ? 'The existing operation may be reused; review its current result before retrying.'
      : null,
  };
}

@Injectable({ providedIn: 'root' })
export class FdService {
  estimate(request:{productId:number;currencyCode:string;principal:number;tenureMonths:number}):Observable<FdEstimate> {
    return this.http.post<FdEstimate>(this.path('/fixed-deposits/estimate'),request);
  }
  private readonly pendingSubmissionStorageKey = 'fd.pending-submissions.v1';
  private readonly inMemorySubmissions = new Map<string, PendingSubmission>();

  constructor(
    private readonly http: HttpClient,
    @Inject(FD_API_BASE_URL) private readonly baseUrl: string
  ) {}

  listJournals(id: number): Observable<Journal[]> {
    return this.http.get<Journal[]>(this.path(`/fixed-deposits/${id}/journals`));
  }
  getFundingSavings(id: number): Observable<SavingsAccount> {
    return this.getAccount(id).pipe(switchMap(account => this.getSavings(account.funding_savings_account_id!)));
  }

  listSavingsCurrencies(): Observable<Array<{ currencyCode: string; currencyName: string; decimalPlaces: number }>> {
    return this.http.get<Array<{ currencyCode: string; currencyName: string; decimalPlaces: number }>>(this.path('/savings-accounts/currencies'));
  }

  transferSavings(request: { fromAccountId: number; toAccountNumber: string; amount: number; remark?: string }, idempotencyKey: string, proof: string): Observable<SavingsTransferResponse> {
    return this.http.post<SavingsTransferResponse>(this.path('/savings-accounts/transfers'), request, { headers: { 'Idempotency-Key': idempotencyKey, 'X-MPIN-Proof': proof } });
  }
  withdrawSavings(request: { fromAccountId:number; amount:number; remark?:string }, idempotencyKey:string, proof:string): Observable<SavingsWithdrawalResponse> {
    return this.http.post<SavingsWithdrawalResponse>(this.path('/savings-accounts/withdrawals'),request,{headers:{'Idempotency-Key':idempotencyKey,'X-MPIN-Proof':proof}});
  }

  savingsTransactions(id: number): Observable<SavingsHistory[]> {
    return this.http.get<SavingsHistory[]>(this.path(`/savings-accounts/${id}/transactions`));
  }

  listSavings(type?: 'SAVINGS' | 'CURRENT'): Observable<SavingsAccount[]> {
    const url=type?this.path(`/savings-accounts?type=${type}`):this.path('/savings-accounts?type=SAVINGS');
    return this.http.get<SavingsAccount[]>(url);
  }

  listCurrentAccounts(): Observable<CurrentAccount[]> { return this.http.get<CurrentAccount[]>(this.path('/current-accounts')); }
  listDepositAccounts(): Observable<SavingsAccount[]> { return this.listSavings().pipe(switchMap(savings=>this.listCurrentAccounts().pipe(map(current=>[...savings,...current])))); }
  listCasaOffers(family:'SAVINGS'|'CURRENT'):Observable<DepositProductOffer[]>{return this.http.get<DepositProductOffer[]>(this.path(`/customer-products/${family}`));}
  getCasaOffer(family:'SAVINGS'|'CURRENT',id:number):Observable<DepositProductOffer>{return this.http.get<DepositProductOffer>(this.path(`/customer-products/${family}/${id}`));}
  createCurrentAccount(body:{productId:number;currencyCode:string;displayName:string},key:string):Observable<CurrentAccount>{return this.http.post<CurrentAccount>(this.path('/current-accounts'),body,{headers:{'Idempotency-Key':key}});}
  getCurrentStatement(id:number,from?:string,to?:string,page=0,size=25):Observable<DepositStatementPage>{const q=new URLSearchParams();if(from)q.set('from',from);if(to)q.set('to',to);q.set('page',String(page));q.set('size',String(size));return this.http.get<DepositStatementPage>(this.path(`/savings-accounts/${id}/statement?${q.toString()}`));}
  currentTransactions(id:number):Observable<SavingsHistory[]>{return this.http.get<SavingsHistory[]>(this.path(`/current-accounts/${id}/transactions`));}
  downloadCurrentStatement(id:number,from?:string,to?:string):Observable<Blob>{const q=new URLSearchParams();if(from)q.set('from',from);if(to)q.set('to',to);return this.http.get(this.path(`/savings-accounts/${id}/statement.csv?${q.toString()}`),{responseType:'blob'});}

  getSavings(id: number): Observable<SavingsAccount> {
    return this.http.get<SavingsAccount>(this.path('/savings-accounts/' + id));
  }

  createSavings(request: { display_name: string; currency_code: string; product_id?: number }, key: string): Observable<SavingsAccount> {
    return this.http.post<SavingsAccount>(this.path('/savings-accounts'), { displayName: request.display_name, currencyCode: request.currency_code, ...(request.product_id?{productId:request.product_id}:{}) },
      { headers: { 'Idempotency-Key': key } }).pipe(retry({
        count: 3,
        delay: (error: unknown, retryCount: number) => {
          if (!(error instanceof HttpErrorResponse) || error.status !== 503
              || error.error?.code !== 'USER_REFERENCE_NOT_READY') return throwError(() => error);
          const seconds = Number(error.headers.get('Retry-After'));
          const wait = Number.isFinite(seconds) ? Math.min(Math.max(seconds, 1), 2) : 1;
          return timer(wait * 1000);
        },
      }));
  }

  listProducts(): Observable<ProductOffer[]> {
    return this.http.get<ProductOffer[]>(this.path('/fd-products/eligible'));
  }

  listAccounts(page = 1, pageSize = 20): Observable<FdAccountListResponse> {
    return this.http.get<BackendFdAccount[]>(this.path('/fixed-deposits')).pipe(map(rows => {
      const accounts = rows.map(accountSummary);
      const start = Math.max(0, page - 1) * pageSize;
      return { data: accounts.slice(start, start + pageSize), pagination: { page, page_size: pageSize, total_records: accounts.length } };
    }));
  }

  book(request: BookingRequest): Observable<AccountResponse> {
    return this.write<BackendFdAccount>('/fixed-deposits', {
      productId: request.product_id,
      savingsAccountId: request.funding_savings_account_id,
      principal: request.principal_amount,
      tenureMonths: request.tenure_months,
      currencyCode: request.currency_code,
      expected_annual_rate: request.expected_annual_rate,
    }).pipe(map(accountView));
  }

  fund(accountId: number, request: FundingRequest,proof:string): Observable<FundingResponse> {
    return this.write<BackendFdAccount>(`/fixed-deposits/${accountId}/fund`, { savingsAccountId: request.savings_account_id },proof).pipe(map(account => ({
      ...accountView(account), funding_recorded: true as const,
      funding_description: 'Internal demonstration transfer from the linked savings account.',
    })));
  }

  getAccount(accountId: number): Observable<AccountResponse> {
    return this.http.get<BackendFdAccount>(this.path(`/fixed-deposits/${accountId}`)).pipe(map(accountView));
  }

  listAccrual(accountId: number): Observable<AccrualResponse[]> {
    return this.http.get<AccrualResponse[]>(this.path(`/fixed-deposits/${accountId}/accruals`));
  }

  getCumulativeAccrual(accountId: number): Observable<AccrualResponse> {
    return this.http.get<AccrualResponse>(this.path(`/fixed-deposits/${accountId}/accruals/cumulative`));
  }

  close(accountId: number,proof:string): Observable<ClosureResponse> {
    return this.write<BackendCloseResponse>(`/fixed-deposits/${accountId}/close`,{},proof).pipe(
      switchMap(result => this.getAccount(accountId).pipe(map(account => ({
        account_id: result.accountId, account_number: account.account_number, status: 'CLOSED' as const,
        closed_date: new Date().toISOString(), settlement_amount: result.settledAmount,
        currency_code: account.currency_code, policy_version: account.policy_version,
        remark: 'Internal demonstration settlement; no external payment was processed.',
      }))))
    );
  }

  requestPrematureClosure(
    accountId: number,
    request: PrematureClosureRequest|undefined,
    proof:string
  ): Observable<PrematureClosureResponse> {
    return this.write<PrematureClosureResponse>(`/fixed-deposits/${accountId}/premature-closure`, request ?? {},proof);
  }

  getPrematureClosures(accountId: number): Observable<PrematureClosureResponse[]> {
    return this.http.get<PrematureClosureResponse[]>(this.path(`/fixed-deposits/${accountId}/premature-closure`));
  }

  listTransactions(accountId: number, query: TransactionQuery = {}): Observable<HistoryEntry[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) {
        params = params.set(key, value);
      }
    }
    return this.http.get<BackendHistoryEntry[]>(this.path(`/fixed-deposits/${accountId}/transactions`), { params }).pipe(map(rows => rows.map(row => ({
      entry_type: normalizeHistoryType(row.entry_type ?? row.event_type ?? ''),
      entry_date: row.entry_date ?? row.occurred_at ?? '',
      amount: row.amount,
      currency_code: row.currency_code ?? '',
      running_balance: row.running_balance ?? row.balance_after ?? null,
      source: row.source ?? (row.record_type === 'ACCRUAL' ? 'ACCRUAL' : 'TRANSACTION'),
      description: row.description ?? row.remarks ?? row.entry_type ?? row.event_type ?? 'Account activity was recorded.',
      source_savings_account_id: row.source_savings_account_id,
    }))));
  }

  hasPendingSubmission(path: string): boolean {
    return this.readSubmissions()[path] !== undefined;
  }

  /** Call only after checking the current account/history state for an uncertain write. */
  clearPendingSubmission(path: string): void {
    const submissions = this.readSubmissions();
    delete submissions[path];
    this.writeSubmissions(submissions);
  }

  private write<T>(path: string, body?: object,proof?:string): Observable<T> {
    let key: string;
    try {
      key = this.getSubmissionKey(path, body);
    } catch (error) {
      return throwError(() => error);
    }

    return this.http.post<T>(this.path(path), body, {
      headers: { 'Idempotency-Key': key, ...(proof?{'X-MPIN-Proof':proof}:{}) },
    }).pipe(tap(() => this.clearSubmissionIfCurrent(path, key)), catchError((error: HttpErrorResponse) => {
      // 4xx responses other than 409 are definitive rejections. A 409, network
      // failure, or server failure can have an uncertain outcome, so preserve
      // the key and require an exact retry or explicit reconciliation.
      if (error.status >= 400 && error.status < 500 && error.status !== 409) {
        this.clearSubmissionIfCurrent(path, key);
      }
      return throwError(() => error);
    }));
  }

  private getSubmissionKey(path: string, body?: object): string {
    const fingerprint = fingerprintRequest(body);
    const submissions = this.readSubmissions();
    const pending = submissions[path];
    if (pending && pending.fingerprint !== fingerprint) {
      throw new HttpErrorResponse({
        status: 409,
        statusText: 'Conflict',
        url: this.path(path),
        error: { message: 'A previous request may have completed. Retry it unchanged, or review My deposits and account history before starting a different request.' },
      });
    }
    if (pending) return pending.key;

    const key = createIdempotencyKey();
    submissions[path] = { key, fingerprint };
    this.writeSubmissions(submissions);
    return key;
  }

  private clearSubmissionIfCurrent(path: string, key: string): void {
    const submissions = this.readSubmissions();
    if (submissions[path]?.key !== key) return;
    delete submissions[path];
    this.writeSubmissions(submissions);
  }

  private readSubmissions(): Record<string, PendingSubmission> {
    if (typeof sessionStorage === 'undefined') {
      return Object.fromEntries(this.inMemorySubmissions.entries());
    }
    try {
      const parsed: unknown = JSON.parse(sessionStorage.getItem(this.pendingSubmissionStorageKey) ?? '{}');
      if (!isPendingSubmissionMap(parsed)) return Object.fromEntries(this.inMemorySubmissions.entries());
      return { ...parsed, ...Object.fromEntries(this.inMemorySubmissions.entries()) };
    } catch {
      return Object.fromEntries(this.inMemorySubmissions.entries());
    }
  }

  private writeSubmissions(submissions: Record<string, PendingSubmission>): void {
    this.inMemorySubmissions.clear();
    for (const [path, value] of Object.entries(submissions)) this.inMemorySubmissions.set(path, value);
    if (typeof sessionStorage === 'undefined') return;
    try {
      sessionStorage.setItem(this.pendingSubmissionStorageKey, JSON.stringify(submissions));
    } catch {
      // The in-memory copy still protects retries for this application session.
    }
  }

  private path(path: string): string {
    return `${this.baseUrl.replace(/\/$/, '')}${path}`;
  }

}

export interface SavingsTransferResponse {
  reference: string;
  fromAccountId: number;
  toAccountNumber: string;
  amount: number;
  currency: string;
  fromBalanceAfter: number;
  postedAt: string;
}
export interface SavingsWithdrawalResponse { reference:string; fromAccountId:number; amount:number; currency:string; balanceAfter:number; postedAt:string; }

export interface SavingsHistory {
  st_id: number;
  st_reference_id: string;
  st_entry_type: 'DEBIT' | 'CREDIT';
  st_amount: number;
  st_currency_id: number;
  st_balance_after: number;
  st_remarks: string;
  st_posted_at: string;
}

interface PendingSubmission {
  key: string;
  fingerprint: string;
}

interface BackendFdAccount {
  account_id: number; account_number: string; product_id: number; product_code: string; product_name: string;
  currency_id: number; currency_code: string; principal_amount: number; tenure_months: number; annual_rate: number;
  compounding_frequency: string; payout_frequency: string; open_date: string; maturity_date: string;
  maturity_amount: number; policy_version: string; status: FdStatus; funding_recorded: boolean;
  funding_savings_account_id: number;
}

interface BackendCloseResponse { accountId: number; status: string; settledAmount: number; savingsBalanceAfter: number; }
interface BackendHistoryEntry {
  record_type?: string; record_id?: number; occurred_at?: string; event_type?: string;
  entry_type?: string; entry_date?: string; source?: 'TRANSACTION' | 'ACCRUAL';
  amount: number; balance_after?: number | null; running_balance?: number | null;
  remarks?: string | null; description?: string; currency_code?: string;
  source_savings_account_id?: number | null;
}

function normalizeHistoryType(value: string): string {
  const type = value.trim().toUpperCase().replace(/[ -]+/g, '_');
  if (type.includes('FUND') || type.includes('DEPOSIT')) return 'FUNDING';
  if (type.includes('INTEREST') || type.includes('ACCRUAL')) return 'ACCRUAL';
  if (type.includes('PENAL')) return 'PENALTY';
  if (type.includes('PAYOUT') || type.includes('SETTLEMENT')) return 'SETTLEMENT';
  if (type.includes('WITHDRAW') || type.includes('CLOSURE')) return 'CLOSURE';
  return type || 'TRANSACTION';
}

function accountView(account: BackendFdAccount): AccountResponse {
  return {
    account_id: account.account_id,
    account_number: account.account_number,
    product_id: account.product_id,
    product_code: account.product_code,
    currency_code: account.currency_code,
    principal_amount: account.principal_amount,
    tenure_months: account.tenure_months,
    annual_rate: account.annual_rate,
    compounding_frequency: account.compounding_frequency,
    payout_frequency: account.payout_frequency,
    open_date: account.open_date,
    maturity_date: account.maturity_date,
    maturity_amount: account.maturity_amount,
    policy_version: account.policy_version,
    status: account.status,
    funding_recorded: account.funding_recorded,
    funding_savings_account_id: account.funding_savings_account_id,
  };
}

function accountSummary(account: BackendFdAccount): FdAccountSummary {
  return {
    account_id: account.account_id,
    account_number: account.account_number,
    product_code: account.product_code,
    product_name: account.product_name,
    currency_code: account.currency_code,
    principal_amount: account.principal_amount,
    tenure_months: account.tenure_months,
    annual_rate: account.annual_rate,
    open_date: account.open_date,
    maturity_date: account.maturity_date,
    maturity_amount: account.maturity_amount,
    status: account.status,
    funding_recorded: account.funding_recorded,
  };
}

function isPendingSubmissionMap(value: unknown): value is Record<string, PendingSubmission> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  return Object.values(value).every((entry) => typeof entry === 'object' && entry !== null
    && typeof (entry as PendingSubmission).key === 'string'
    && typeof (entry as PendingSubmission).fingerprint === 'string');
}

function fingerprintRequest(value: unknown): string {
  return JSON.stringify(sortJsonValue(value ?? null));
}

function sortJsonValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortJsonValue);
  if (typeof value !== 'object' || value === null) return value;
  return Object.fromEntries(Object.entries(value as Record<string, unknown>)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, nested]) => [key, sortJsonValue(nested)]));
}

function createIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}

function isApiErrorEnvelope(value: unknown): value is ApiErrorEnvelope {
  return typeof value === 'object' && value !== null;
}

function safeMessage(status: number): string {
  switch (status) {
    case 400: return 'Please check the submitted details.';
    case 401: return 'Your session has expired. Please sign in again.';
    case 404: return 'The requested FD record was not found.';
    case 409: return 'The FD operation conflicts with its current state.';
    case 422: return 'This FD operation is not supported for the selected product.';
    default: return 'The FD service is temporarily unavailable.';
  }
}
