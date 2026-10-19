import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';
import { inject } from '@angular/core';

export interface CustomerAdminRecord {
  id: number;
  customerNumber: string;
  username: string;
  fullName: string;
  kycStatus: string;
  riskCategory: string;
  legalEntityType: string;
}

export interface CustomerAdminPage {
  content: CustomerAdminRecord[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class CustomerAdminService {
  private readonly baseUrl = inject(API_BASE_URL);

  constructor(private readonly http: HttpClient) {}

  list(page: number, search: string): Observable<CustomerAdminPage> {
    let params = new HttpParams().set('page', page).set('size', 10);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<CustomerAdminPage>(apiUrl(this.baseUrl, '/api/v1/customers'), { params });
  }

  update(customerId: number, update: Pick<CustomerAdminRecord, 'fullName' | 'kycStatus' | 'riskCategory' | 'legalEntityType'>): Observable<CustomerAdminRecord> {
    return this.http.put<CustomerAdminRecord>(apiUrl(this.baseUrl, `/api/v1/customers/${customerId}`), update);
  }
}
