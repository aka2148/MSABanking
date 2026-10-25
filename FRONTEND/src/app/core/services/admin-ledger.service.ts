import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BACKEND_API_BASE_URL } from './fd-product-admin.service';

export interface AdminLedgerLine {
  accountCode: string;
  debit: number;
  credit: number;
}

export interface AdminLedgerEntry {
  journalId: number;
  operation: string;
  postedAt: string;
  savingsAccountId: number;
  savingsAccountNumber: string;
  fixedDepositId: number | null;
  fixedDepositAccountNumber: string | null;
  currencyCode: string;
  totalDebit: number;
  totalCredit: number;
  savingsBalanceAfter: number | null;
  lines: AdminLedgerLine[];
}

export interface AdminLedgerPage {
  content: AdminLedgerEntry[];
  number: number;
  size: number;
  totalElements: number;
}

@Injectable({ providedIn: 'root' })
export class AdminLedgerService {
  constructor(private readonly http: HttpClient, @Inject(BACKEND_API_BASE_URL) private readonly base: string) {}

  entries(page = 0, accountId: number | null = null, fixedDepositId: number | null = null): Observable<AdminLedgerPage> {
    let params = new HttpParams().set('page', page).set('pageSize', 25);
    if (accountId !== null) params = params.set('accountId', accountId);
    if (fixedDepositId !== null) params = params.set('fixedDepositId', fixedDepositId);
    return this.http.get<AdminLedgerPage>(`${this.base.replace(/\/$/, '')}/api/v1/admin/ledger`, { params });
  }
}
