import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';

export interface CurrencyTotals {
  currencyCode: string;
  decimalPlaces: number;
  activeCount: number;
  totalPrincipal: number;
  totalAccruedInterest: number;
  averageInterestRate: number;
}

export interface DepositView {
  accountId: number;
  accountNumber: string;
  customerId: number;
  username: string;
  principal: number;
  currencyCode: string;
  annualRate: number;
  openDate: string;
  maturityDate: string;
  maturityAmount: number;
  status: string;
}

export interface PortfolioReport {
  customerUsername: string;
  fullName: string;
  currencies: CurrencyTotals[];
  accounts: DepositView[];
}

export interface FdSummaryRow {
  productCode: string;
  productName: string;
  currencyCode: string;
  decimalPlaces: number;
  status: string;
  totalAccounts: number;
  totalPrincipal: number;
  totalAccruedInterest: number;
  storedMaturityAmountTotal: number;
}

export interface MaturitySummaryRow {
  maturityDate: string;
  productCode: string;
  productName: string;
  currencyCode: string;
  decimalPlaces: number;
  accountsDue: number;
  totalPrincipal: number;
  totalAccruedInterest: number;
  storedMaturityAmountTotal: number;
}

export interface InterestHistoryRow {
  accountId: number;
  accountNumber: string;
  accrualDate: string;
  accruedInterest: number;
  cumulativeAccruedInterest: number;
  capitalizedAmount: number;
  currencyCode: string;
  decimalPlaces: number;
}

export interface StatementRecord {
  statementId: number;
  accountId: number;
  accountNumber: string;
  currencyCode: string;
  decimalPlaces: number;
  periodStart: string | null;
  periodEnd: string | null;
  generatedAt: string | null;
  contractedPrincipal: number;
  interestAccruedInPeriod: number;
  accountStatus: string;
}

export interface NoticeRecord {
  noticeId: number;
  accountId: number;
  accountNumber: string;
  currencyCode: string;
  decimalPlaces: number;
  noticeType: string;
  recordedAt: string | null;
  channel: string;
  deliveryStatus: string;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  constructor(private readonly http: HttpClient, @Inject(API_BASE_URL) private readonly baseUrl: string) {}

  portfolio(username?: string): Observable<PortfolioReport> {
    const path = username ? `/api/reports/portfolio/${encodeURIComponent(username)}` : '/api/reports/portfolio';
    return this.http.get<PortfolioReport>(apiUrl(this.baseUrl, path));
  }

  fdSummary(): Observable<FdSummaryRow[]> {
    return this.http.get<FdSummaryRow[]>(apiUrl(this.baseUrl, '/api/reports/fd-summary'));
  }

  maturitySummary(): Observable<MaturitySummaryRow[]> {
    return this.http.get<MaturitySummaryRow[]>(apiUrl(this.baseUrl, '/api/reports/maturity-summary'));
  }

  interestHistory(username?: string): Observable<InterestHistoryRow[]> {
    const path = username ? `/api/reports/interest-history/customer/${encodeURIComponent(username)}` : '/api/reports/interest-history';
    return this.http.get<InterestHistoryRow[]>(apiUrl(this.baseUrl, path));
  }

  statements(username?: string): Observable<StatementRecord[]> {
    const path = username ? `/api/reports/statements/customer/${encodeURIComponent(username)}` : '/api/reports/statements';
    return this.http.get<StatementRecord[]>(apiUrl(this.baseUrl, path));
  }

  notices(username?: string): Observable<NoticeRecord[]> {
    const path = username ? `/api/reports/notices/customer/${encodeURIComponent(username)}` : '/api/reports/notices';
    return this.http.get<NoticeRecord[]>(apiUrl(this.baseUrl, path));
  }

  downloadCsv(report: 'portfolio' | 'fd-summary' | 'maturity-summary' | 'interest-history' | 'statements' | 'notices', username?: string): Observable<Blob> {
    const suffix = username && report !== 'fd-summary' && report !== 'maturity-summary'
      ? `/customer/${encodeURIComponent(username)}`
      : '';
    const path = username && report === 'portfolio'
      ? `/api/reports/portfolio/${encodeURIComponent(username)}.csv`
      : `/api/reports/${report}${suffix}.csv`;
    return this.http.get(apiUrl(this.baseUrl, path), { responseType: 'blob' });
  }
}
