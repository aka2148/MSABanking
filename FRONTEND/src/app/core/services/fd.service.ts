import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Inject, Injectable, InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AccountResponse,
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
} from '../models/fd.models';

export const FD_API_BASE_URL = new InjectionToken<string>('FD_API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080/fd/v1',
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
  private activeSubmissionKey: string | null = null;

  constructor(
    private readonly http: HttpClient,
    @Inject(FD_API_BASE_URL) private readonly baseUrl: string
  ) {}

  listProducts(): Observable<ProductOffer[]> {
    return this.http.get<ProductOffer[]>(this.path('/products'));
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

  startSubmission(): string {
    return this.activeSubmissionKey ?? (this.activeSubmissionKey = createIdempotencyKey());
  }

  completeSubmission(): void {
    this.activeSubmissionKey = null;
  }

  abandonSubmission(): void {
    this.activeSubmissionKey = null;
  }

  private write<T>(path: string, body?: object): Observable<T> {
    const key = this.startSubmission();
    return this.http.post<T>(this.path(path), body, {
      headers: { 'Idempotency-Key': key },
    });
  }

  private path(path: string): string {
    return `${this.baseUrl.replace(/\/$/, '')}${path}`;
  }
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
