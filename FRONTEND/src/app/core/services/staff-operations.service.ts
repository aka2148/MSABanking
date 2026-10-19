import { Inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BACKEND_API_BASE_URL } from './fd-product-admin.service';

export interface StaffPage<T> { content: T[]; number: number; size: number; totalElements: number; }
export interface StaffFdRow { id: number; accountNumber: string; status: string; principal: number; currency: string; rate: number | null; tenureMonths: number; openedAt: string; maturityAt: string | null; customerName: string | null; productName: string | null; }
export interface StaffSavingsRow { id: number; accountNumber: string; displayName: string; balance: number; currency: string; decimalPlaces: number; status: string; createdAt: string; customerName: string | null; }
export interface StaffTransaction { st_id: number; st_reference_id: string; st_entry_type: 'CREDIT' | 'DEBIT'; st_amount: number; st_balance_after: number; st_remarks: string; st_posted_at: string; }

@Injectable({ providedIn: 'root' })
export class StaffOperationsService {
  constructor(private readonly http: HttpClient, @Inject(BACKEND_API_BASE_URL) private readonly base: string) {}

  fixedDeposits(page = 0, status = ''): Observable<StaffPage<StaffFdRow>> {
    return this.http.get<StaffPage<StaffFdRow>>(this.path('/fd/v1/staff/accounts'), { params: this.params(page, status) });
  }
  savings(page = 0, status = ''): Observable<StaffPage<StaffSavingsRow>> {
    return this.http.get<StaffPage<StaffSavingsRow>>(this.path('/fd/v1/staff/savings-accounts'), { params: this.params(page, status) });
  }
  transactions(id: number): Observable<StaffTransaction[]> {
    return this.http.get<StaffTransaction[]>(this.path(`/fd/v1/staff/savings-accounts/${id}/transactions`));
  }
  transact(id: number, action: 'deposit' | 'withdraw', amount: number, remarks: string, idempotencyKey: string): Observable<StaffSavingsRow> {
    return this.http.post<StaffSavingsRow>(this.path(`/fd/v1/staff/savings-accounts/${id}/${action}`), { amount, remarks }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }
  private params(page: number, status: string): HttpParams {
    let params = new HttpParams().set('page', page).set('pageSize', 8);
    if (status) params = params.set('status', status);
    return params;
  }
  private path(path: string): string { return `${this.base.replace(/\/$/, '')}${path}`; }
}
