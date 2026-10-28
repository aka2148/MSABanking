import { Inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BACKEND_API_BASE_URL } from './fd-product-admin.service';
import { StaffPrematureQuotePage, PrematureClosureResponse } from '../models/fd.models';
import { DepositProductOffer } from '../models/fd.models';

export interface StaffPage<T> { content: T[]; number: number; size: number; totalElements: number; }
export interface StaffFdRow { id: number; accountNumber: string; status: string; principal: number; currency: string; rate: number | null; tenureMonths: number; openedAt: string; maturityAt: string | null; customerName: string | null; productName: string | null; }
export interface StaffSavingsRow { id: number; accountNumber: string; displayName: string; balance: number; currency: string; decimalPlaces: number; status: string; createdAt: string; customerName: string | null; customerUserId:number;transactionCount: number;accountType:'SAVINGS'|'CURRENT';productId:number|null;productName:string|null;minimumBalance:number;availableBalance:number; }
export interface StaffTransaction { st_id: number; st_reference_id: string; st_entry_type: 'CREDIT' | 'DEBIT'; st_amount: number; st_balance_after: number; st_remarks: string; st_posted_at: string; }

@Injectable({ providedIn: 'root' })
export class StaffOperationsService {
  constructor(private readonly http: HttpClient, @Inject(BACKEND_API_BASE_URL) private readonly base: string) {}

  fixedDeposits(page = 0, status = '', pageSize = 8): Observable<StaffPage<StaffFdRow>> {
    return this.http.get<StaffPage<StaffFdRow>>(this.path('/fd/v1/staff/accounts'), { params: this.params(page, status, pageSize) });
  }
  savings(page = 0, status = '', pageSize = 8,type=''): Observable<StaffPage<StaffSavingsRow>> {
    let params=this.params(page,status,pageSize);if(type)params=params.set('type',type);
    return this.http.get<StaffPage<StaffSavingsRow>>(this.path('/fd/v1/staff/savings-accounts'), { params });
  }
  depositStatus(id:number,action:'FREEZE'|'UNFREEZE'|'CLOSE',reason:string):Observable<unknown>{return this.http.post(this.path(`/fd/v1/staff/savings-accounts/${id}/status`),{action,reason});}
  runSavingsInterest():Observable<unknown>{return this.http.post(this.path('/api/v1/admin/savings-interest/run'),{});}
  depositOffers(family:'SAVINGS'|'CURRENT'):Observable<DepositProductOffer[]>{return this.http.get<DepositProductOffer[]>(this.path(`/api/v1/customer-products/${family}`));}
  depositCurrencies():Observable<Array<{currencyCode:string;decimalPlaces:number}>>{return this.http.get<Array<{currencyCode:string;decimalPlaces:number}>>(this.path('/api/v1/savings-accounts/currencies'));}
  openDepositForCustomer(type:'SAVINGS'|'CURRENT',body:{userId:number;productId:number;currencyCode:string;displayName:string},key:string):Observable<unknown>{const current=type==='CURRENT',url=current?'/api/v1/current-accounts/admin':'/api/v1/savings-accounts/admin',payload=current?body:{userId:body.userId,...(body.productId>0?{productId:body.productId}:{}),currencyCode:body.currencyCode,displayName:body.displayName};return this.http.post(this.path(url),payload,{headers:{'Idempotency-Key':key}});}
  demoOptions(): Observable<{ enabled: boolean }> { return this.http.get<{ enabled: boolean }>(this.path('/api/v1/savings-accounts/demo-options')); }
  demoOpening(id: number, amount: number, idempotencyKey: string): Observable<unknown> {
    return this.http.post(this.path(`/api/v1/savings-accounts/${id}/demo-opening`), { amount }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }
  transactions(id: number): Observable<StaffTransaction[]> {
    return this.http.get<StaffTransaction[]>(this.path(`/fd/v1/staff/savings-accounts/${id}/transactions`));
  }
  transact(id: number, action: 'deposit' | 'withdraw', amount: number, remarks: string, idempotencyKey: string,attestation?: {verificationMethod:string;verificationReference?:string}): Observable<StaffSavingsRow> {
    return this.http.post<StaffSavingsRow>(this.path(`/fd/v1/staff/savings-accounts/${id}/${action}`), { amount, remarks, ...attestation }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }
  customerIdForUser(userId:number):Observable<{id:number}>{return this.http.get<{id:number}>(this.path(`/api/v1/customers/by-user/${userId}`));}
  pendingPrematureClosures(page = 0, pageSize = 25): Observable<StaffPrematureQuotePage> {
    return this.http.get<StaffPrematureQuotePage>(this.path('/api/v1/admin/fixed-deposits/premature-closures'), {
      params: new HttpParams().set('page', page).set('pageSize', pageSize),
    });
  }
  approvePrematureClosure(accountId: number, closureId: number, idempotencyKey: string): Observable<unknown> {
    return this.http.post(this.path(`/api/v1/admin/fixed-deposits/${accountId}/premature-closure/${closureId}/approve`), {}, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
  rejectPrematureClosure(accountId: number, closureId: number, reason: string, idempotencyKey: string): Observable<PrematureClosureResponse> {
    return this.http.post<PrematureClosureResponse>(this.path(`/api/v1/admin/fixed-deposits/${accountId}/premature-closure/${closureId}/reject`), { reason }, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
  private params(page: number, status: string, pageSize: number): HttpParams {
    let params = new HttpParams().set('page', page).set('pageSize', pageSize);
    if (status) params = params.set('status', status);
    return params;
  }
  private path(path: string): string { return `${this.base.replace(/\/$/, '')}${path}`; }
}
