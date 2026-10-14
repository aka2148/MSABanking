import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Inject, Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, catchError, tap, throwError } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';
import {
  AccountResponse,
  SavingsAccount,
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
  FdAccountListResponse,
} from '../models/fd.models';

export const FD_API_BASE_URL = new InjectionToken<string>('FD_API_BASE_URL', {
  providedIn: 'root',
  factory: () => apiUrl(inject(API_BASE_URL), '/fd/v1'),
});

export interface TransactionQuery {
  from_date?: string;
  to_date?: string;
  txn_type_id?: number;
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
  private readonly pendingSubmissionStorageKey = 'fd.pending-submissions.v1';
  private readonly inMemorySubmissions = new Map<string, PendingSubmission>();

  constructor(
    private readonly http: HttpClient,
    @Inject(FD_API_BASE_URL) private readonly baseUrl: string
  ) {}

  listJournals(id: number): Observable<Journal[]> {
    return this.http.get<Journal[]>(this.path('/accounts/' + id + '/journals'));
  }
  getFundingSavings(id: number): Observable<SavingsAccount> {
    return this.http.get<SavingsAccount>(this.path('/accounts/' + id + '/funding-savings'));
  }

  demoOptions(): Observable<{ enabled: boolean }> {
    return this.http.get<{ enabled: boolean }>(this.path('/savings-accounts/demo-options'));
  }

  provisionDemoOpening(id: number, amount: number): Observable<SavingsAccount> {
    return this.http.post<SavingsAccount>(this.path('/savings-accounts/' + id + '/demo-opening'), { amount });
  }

  listSavings(): Observable<SavingsAccount[]> {
    return this.http.get<SavingsAccount[]>(this.path('/savings-accounts'));
  }

  getSavings(id: number): Observable<SavingsAccount> {
    return this.http.get<SavingsAccount>(this.path('/savings-accounts/' + id));
  }

  createSavings(request: { display_name: string; currency_code: string }, key: string): Observable<SavingsAccount> {
    return this.http.post<SavingsAccount>(this.path('/savings-accounts'), request,
      { headers: { 'Idempotency-Key': key } });
  }

  listProducts(): Observable<ProductOffer[]> {
    return this.http.get<ProductOffer[]>(this.path('/products'));
  }

  listAccounts(page = 1, pageSize = 20): Observable<FdAccountListResponse> {
    const params = new HttpParams().set('page', page).set('page_size', pageSize);
    return this.http.get<FdAccountListResponse>(this.path('/accounts'), { params });
  }

  book(request: BookingRequest): Observable<AccountResponse> {
    return this.write<AccountResponse>('/accounts', request);
  }

  fund(accountId: number, request: FundingRequest): Observable<FundingResponse> {
    return this.write<FundingResponse>(`/accounts/${accountId}/fund`, request);
  }

  getAccount(accountId: number): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(this.path(`/accounts/${accountId}`));
  }

  listAccrual(accountId: number): Observable<AccrualResponse[]> {
    return this.http.get<AccrualResponse[]>(this.path(`/accounts/${accountId}/accrual`));
  }

  getCumulativeAccrual(accountId: number): Observable<AccrualResponse> {
    return this.http.get<AccrualResponse>(this.path(`/accounts/${accountId}/accrual/cumulative`));
  }

  close(accountId: number): Observable<ClosureResponse> {
    return this.write<ClosureResponse>(`/accounts/${accountId}/close`);
  }

  requestPrematureClosure(
    accountId: number,
    request?: PrematureClosureRequest
  ): Observable<PrematureClosureResponse> {
    return this.write<PrematureClosureResponse>(`/accounts/${accountId}/premature-closure`, request);
  }

  getPrematureClosures(accountId: number): Observable<PrematureClosureResponse[]> {
    return this.http.get<PrematureClosureResponse[]>(this.path(`/accounts/${accountId}/premature-closure`));
  }

  approvePrematureClosure(accountId: number, closureId: number): Observable<PrematureClosureResponse> {
    return this.write<PrematureClosureResponse>(
      `/accounts/${accountId}/premature-closure/${closureId}/approve`
    );
  }

  listTransactions(accountId: number, query: TransactionQuery = {}): Observable<HistoryEntry[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) {
        params = params.set(key, value);
      }
    }
    return this.http.get<HistoryEntry[]>(this.path(`/accounts/${accountId}/transactions`), { params });
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

  private write<T>(path: string, body?: object): Observable<T> {
    let key: string;
    try {
      key = this.getSubmissionKey(path, body);
    } catch (error) {
      return throwError(() => error);
    }

    return this.http.post<T>(this.path(path), body, {
      headers: { 'Idempotency-Key': key },
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

interface PendingSubmission {
  key: string;
  fingerprint: string;
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
