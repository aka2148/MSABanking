import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';

export interface CustomerOffer {
  productId: number;
  productCode: string;
  productName: string;
  description: string;
  family: 'LOAN' | 'FOREX';
  baseCurrencyCode: string;
  currencies: Array<{ code: string; minimumAmount: number; maximumAmount: number; decimalPlaces: number }>;
  pricingBands: Array<{ currencyCode: string; valueFrom: number; valueTo: number; rate: number }>;
  notice: string;
}

export interface LoanApplication {
  id: number;
  customerId: number;
  customerName: string;
  customerNumber: string;
  productId: number;
  productName: string;
  requestedAmount: number;
  approvedAmount: number | null;
  currencyCode: string;
  tenureMonths: number;
  annualRate: number;
  purpose: string;
  destinationSavingsAccountId: number;
  destinationAccountName: string;
  destinationAccountNumber: string;
  status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'DISBURSED' | 'ACTIVE' | 'CLOSED';
  decisionAdminId: number | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  disbursementTransactionId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface LoanEvent { id: number; status: string; actorId: number; reason: string | null; createdAt: string; }
export interface ForexRequest {
  id: number; productId: number; productName: string; amount: number; fundingCurrency: string; destinationCurrency: string;
  markupRate: number; fee: number; status: 'REQUESTED' | 'CANCELLED'; createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class LoanForexService {
  constructor(private readonly http: HttpClient, @Inject(API_BASE_URL) private readonly base: string) {}

  offers(family: 'LOAN' | 'FOREX'): Observable<CustomerOffer[]> {
    return this.http.get<CustomerOffer[]>(apiUrl(this.base, `/api/v1/customer-products/${family}`));
  }
  offer(family: 'LOAN' | 'FOREX', id: number): Observable<CustomerOffer> {
    return this.http.get<CustomerOffer>(apiUrl(this.base, `/api/v1/customer-products/${family}/${id}`));
  }
  loans(): Observable<LoanApplication[]> { return this.http.get<LoanApplication[]>(apiUrl(this.base, '/api/v1/loans')); }
  loan(id: number): Observable<LoanApplication> { return this.http.get<LoanApplication>(apiUrl(this.base, `/api/v1/loans/${id}`)); }
  loanHistory(id: number): Observable<LoanEvent[]> { return this.http.get<LoanEvent[]>(apiUrl(this.base, `/api/v1/loans/${id}/history`)); }
  apply(body: { productId: number; amount: number; tenureMonths: number; currencyCode: string; purpose: string; destinationSavingsAccountId: number }, key: string,proof:string): Observable<LoanApplication> {
    return this.http.post<LoanApplication>(apiUrl(this.base, '/api/v1/loans'), body, { headers: { 'Idempotency-Key': key,'X-MPIN-Proof':proof } });
  }
  adminLoans(status = 'PENDING_REVIEW'): Observable<LoanApplication[]> {
    return this.http.get<LoanApplication[]>(apiUrl(this.base, '/api/v1/admin/loans'), { params: { status } });
  }
  adminLoan(id: number): Observable<LoanApplication> { return this.http.get<LoanApplication>(apiUrl(this.base, `/api/v1/admin/loans/${id}`)); }
  adminLoanHistory(id: number): Observable<LoanEvent[]> { return this.http.get<LoanEvent[]>(apiUrl(this.base, `/api/v1/admin/loans/${id}/history`)); }
  approve(id: number): Observable<LoanApplication> { return this.http.post<LoanApplication>(apiUrl(this.base, `/api/v1/admin/loans/${id}/approve`), {}); }
  reject(id: number, reason: string): Observable<LoanApplication> { return this.http.post<LoanApplication>(apiUrl(this.base, `/api/v1/admin/loans/${id}/reject`), { reason }); }
  disburse(id: number): Observable<LoanApplication> { return this.http.post<LoanApplication>(apiUrl(this.base, `/api/v1/admin/loans/${id}/disburse`), {}); }
  forexRequests(): Observable<ForexRequest[]> { return this.http.get<ForexRequest[]>(apiUrl(this.base, '/api/v1/forex-requests')); }
  forexRequest(id: number): Observable<ForexRequest> { return this.http.get<ForexRequest>(apiUrl(this.base, `/api/v1/forex-requests/${id}`)); }
  requestForex(body: { productId: number; amount: number; fundingCurrencyCode: string; destinationCurrencyCode: string }, key: string,proof:string): Observable<ForexRequest> {
    return this.http.post<ForexRequest>(apiUrl(this.base, '/api/v1/forex-requests'), body, { headers: { 'Idempotency-Key': key,'X-MPIN-Proof':proof } });
  }
}
