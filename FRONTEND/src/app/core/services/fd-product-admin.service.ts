import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';

export const BACKEND_API_BASE_URL = new InjectionToken<string>('BACKEND_API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(API_BASE_URL),
});

export interface FdOfferAdmin {
  fd_product_id: number;
  catalog_product_id: number;
  product_code: string;
  product_name: string;
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE';
  currency_codes: string[];
}

export interface CatalogProductDetails {
  productId: number;
  productCode: string;
  productName: string;
  categoryId: number;
  baseCurrencyCode: string | null;
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE';
  effectiveFrom: string | null;
  effectiveTo: string | null;
  description: string | null;
}

export interface FdOfferUpdateRequest { productName: string; description: string; }

@Injectable({ providedIn: 'root' })
export class FdProductAdminService {
  constructor(
    private readonly http: HttpClient,
    @Inject(BACKEND_API_BASE_URL) private readonly backendUrl: string,
  ) {}

  listOffers(): Observable<FdOfferAdmin[]> {
    return this.http.get<FdOfferAdmin[]>(this.path('/api/v1/fd-products'));
  }

  activateOffer(fdProductId: number): Observable<FdOfferAdmin> {
    return this.http.post<FdOfferAdmin>(this.path(`/api/v1/fd-products/${fdProductId}/activate`), {});
  }

  deactivateOffer(fdProductId: number): Observable<FdOfferAdmin> {
    return this.http.post<FdOfferAdmin>(this.path(`/api/v1/fd-products/${fdProductId}/deactivate`), {});
  }

  getProduct(productId: number): Observable<CatalogProductDetails> {
    return this.http.get<CatalogProductDetails>(this.path(`/api/v1/products/${productId}`));
  }

  updateOffer(productId: number, request: FdOfferUpdateRequest): Observable<FdOfferAdmin> {
    return this.http.put<FdOfferAdmin>(this.path(`/api/v1/fd-products/${productId}`), request);
  }

  saveTranslation(productId: number, locale: string, productName: string, description: string): Observable<void> {
    return this.http.put<void>(this.path(`/api/v1/products/${productId}/translations/${encodeURIComponent(locale)}`), { productName, description });
  }

  private path(path: string): string {
    return apiUrl(this.backendUrl, path);
  }
}
