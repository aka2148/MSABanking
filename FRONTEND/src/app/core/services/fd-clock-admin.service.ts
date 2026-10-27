import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BACKEND_API_BASE_URL } from './fd-product-admin.service';

export interface FdBatchRunSummary {
  business_date: string;
  accounts_examined: number;
  accruals_processed: number;
  accrual_days_recorded: number;
  maturities_processed: number;
  accounts_failed: number;
  failed_account_ids: number[];
}

export interface FdClockStatus {
  system_time: string;
  effective_time: string;
  business_date: string;
  business_zone: string;
  simulation_active: boolean;
  batch_run: FdBatchRunSummary | null;
}

@Injectable({ providedIn: 'root' })
export class FdClockAdminService {
  constructor(
    private readonly http: HttpClient,
    @Inject(BACKEND_API_BASE_URL) private readonly backendUrl: string,
  ) {}

  getStatus(): Observable<FdClockStatus> {
    return this.http.get<FdClockStatus>(this.path('/api/admin/clock'));
  }

  advance(days: number): Observable<FdClockStatus> {
    return this.http.post<FdClockStatus>(this.path('/api/admin/clock/advance'), { days });
  }

  reset(): Observable<FdClockStatus> {
    return this.http.post<FdClockStatus>(this.path('/api/admin/clock/reset'), {});
  }

  private path(path: string): string {
    return `${this.backendUrl.replace(/\/$/, '')}${path}`;
  }
}
