import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BACKEND_API_BASE_URL } from './fd-product-admin.service';

export interface ComplianceRateResult {
  productId: number;
  currencyCode: string;
  principalAmount: number;
  tenureMonths: number;
  displayedRate: number;
  authoritativeRate: number | null;
  matchStatus: string;
  isMatch: boolean;
  difference: number | null;
  explanation: string;
  compoundingFrequency: string | null;
  payoutFrequency: string | null;
  evaluationDate: string;
}

export interface ComplianceDateResult {
  productId: number;
  currencyCode: string;
  principalAmount: number;
  tenureMonths: number;
  evaluationDate: string;
  validityStatus: string;
  isValid: boolean;
  applicableRate: number | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  explanation: string;
}

export interface ComplianceClock {
  business_date: string;
  system_date: string;
  business_zone: string;
}

export interface CurrencyLimitSummary {
  currencyCode: string;
  minimumAmount: number;
  maximumAmount: number;
  isBaseCurrency: boolean;
}

export interface RateBandSummary {
  ruleId: number;
  currencyCode: string;
  minimumTenureMonths: number;
  maximumTenureMonths: number;
  annualRate: number;
  compoundingBasis: string;
  payoutFrequency: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  isActive: boolean;
  status: 'ACTIVE' | 'FUTURE' | 'EXPIRED' | 'DRAFT' | 'INACTIVE' | string;
}

export interface ProductSchedule {
  productId: number;
  productCode: string;
  productName: string;
  status: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  currencies: CurrencyLimitSummary[];
  rateBands: RateBandSummary[];
}

export interface RateBandInput {
  currencyCode: string;
  minimumTenureMonths: number;
  maximumTenureMonths: number;
  annualRate: number;
  compoundingBasis: string;
  payoutFrequency: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

export interface UpdateScheduleRequest {
  effectiveFrom: string | null;
  effectiveTo: string | null;
  rateBands: RateBandInput[];
}

@Injectable({ providedIn: 'root' })
export class FdComplianceService {
  constructor(private readonly http: HttpClient, @Inject(BACKEND_API_BASE_URL) private readonly base: string) {}

  clock(): Observable<ComplianceClock> {
    return this.http.get<ComplianceClock>(this.path('/api/v1/compliance/fd/clock'));
  }

  verifyRate(payload: { product_id: number; currency_code: string; principal_amount: number; tenure_months: number; displayed_rate: number; evaluation_date: string }): Observable<ComplianceRateResult> {
    return this.http.post<ComplianceRateResult>(this.path('/api/v1/compliance/fd/verify-rate-disclosure'), payload);
  }

  evaluateDate(payload: { product_id: number; currency_code: string; principal_amount: number; tenure_months: number; evaluation_date: string }): Observable<ComplianceDateResult> {
    return this.http.post<ComplianceDateResult>(this.path('/api/v1/compliance/fd/evaluate-effective-date'), payload);
  }

  getSchedule(productId: number): Observable<ProductSchedule> {
    return this.http.get<ProductSchedule>(this.path(`/api/v1/compliance/fd/products/${productId}/schedule`));
  }

  updateSchedule(productId: number, payload: UpdateScheduleRequest): Observable<ProductSchedule> {
    return this.http.put<ProductSchedule>(this.path(`/api/v1/compliance/fd/products/${productId}/schedule`), payload);
  }

  private path(path: string): string {
    return `${this.base.replace(/\/$/, '')}${path}`;
  }
}
