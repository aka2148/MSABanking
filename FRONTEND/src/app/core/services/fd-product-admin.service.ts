import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';

export const BACKEND_API_BASE_URL = new InjectionToken<string>('BACKEND_API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(API_BASE_URL),
});

export interface ProductCategoryOption {
  categoryId: number;
  categoryCode: string;
  categoryName: string;
}

export interface FdOfferAdmin {
  fd_product_id: number;
  catalog_product_id: number;
  product_code: string;
  product_name: string;
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE';
  currency_codes: string[];
}

export interface FdOfferCreateRequest {
  productCode: string;
  productName: string;
  categoryId: number;
  description: string;
  currencies: Array<{
    currencyCode: 'USD' | 'INR';
    minimumAmount: number;
    maximumAmount: number;
    rateBands: Array<{
      minimumTenureMonths: number;
      maximumTenureMonths: number;
      annualRate: number;
    }>;
  }>;
}

@Injectable({ providedIn: 'root' })
export class FdProductAdminService {
  constructor(
    private readonly http: HttpClient,
    @Inject(BACKEND_API_BASE_URL) private readonly backendUrl: string,
  ) {}

  listCategories(): Observable<ProductCategoryOption[]> {
    return this.http.get<ProductCategoryOption[]>(this.path('/api/v1/product-categories'));
  }

  listOffers(): Observable<FdOfferAdmin[]> {
    return this.http.get<FdOfferAdmin[]>(this.path('/api/v1/fd-products'));
  }

  createOffer(request: FdOfferCreateRequest): Observable<FdOfferAdmin> {
    return this.http.post<FdOfferAdmin>(this.path('/api/v1/fd-products'), request);
  }

  activateOffer(fdProductId: number): Observable<FdOfferAdmin> {
    return this.http.post<FdOfferAdmin>(this.path(`/api/v1/fd-products/${fdProductId}/activate`), {});
  }

  private path(path: string): string {
    return apiUrl(this.backendUrl, path);
  }
}
