import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BACKEND_API_BASE_URL } from './fd-product-admin.service';

export type ProductFamily = 'LOAN' | 'CASA' | 'FIXED_DEPOSIT' | 'FOREX' | 'UNKNOWN';

export interface CategoryOption {
  id: number;
  code: string;
  name: string;
  parentId: number | null;
  family: ProductFamily;
}

export interface CurrencyOption {
  id: number;
  code: string;
  name: string;
  symbol: string;
  decimalPlaces: number;
}

export interface AttributeOption {
  id: number;
  categoryId: number;
  key: string;
  dataType: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN';
  displayName: string;
  required: boolean;
  choices: string[];
  unit: string | null;
  defaultValue: string | null;
  minimumValue: number | null;
  maximumValue: number | null;
  displayOrder: number;
  behaviorKey: string | null;
  inherited: boolean;
}

export interface RoleOption { id: number; code: string; description: string; }
export interface FactOption {
  code: string;
  dataType: string;
  operators: string[];
  choices: string[];
  executable: boolean;
  dependency: string | null;
}

export interface ProductBuilderMetadata {
  categories: CategoryOption[];
  currencies: CurrencyOption[];
  attributes: AttributeOption[];
  roles: RoleOption[];
  facts: FactOption[];
  operators: string[];
  outcomeTypes: string[];
  calculationMethods: string[];
}

export interface ProductConfigurationRequest {
  productCode: string;
  productName: string;
  categoryId: number;
  description: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  baseCurrencyCode: string;
  currencies: Array<{ currencyCode: string; minimumAmount: number; maximumAmount: number }>;
  attributes: Array<{ definitionId: number; value: string }>;
  eligibilityRules: Array<{
    name: string; fact: string; dataType: string; operator: string;
    valueFrom: string; valueTo: string; valueSet: string; combineLogic: 'AND' | 'OR';
  }>;
  pricingBands: Array<{
    currencyCode: string; fact: string; valueFrom: number; valueTo: number; rate: number;
    outcomeType: string; rateUnit: 'DECIMAL_FRACTION'; calculationMethod: string;
  }>;
  charges: Array<{
    chargeCode: string; calculationBasis: 'FIXED' | 'PERCENTAGE';
    slabs: Array<{
      fact: string; valueFrom: number; valueTo: number; value: number;
      unit: 'FIXED_AMOUNT' | 'DECIMAL_FRACTION'; currencyCode: string;
      floor: number | null; cap: number | null;
    }>;
  }>;
  roleConstraints: Array<{
    roleTypeId: number; mandatory: boolean; minimumCount: number | null; maximumCount: number | null;
  }>;
}

export interface ValidationResult { valid: boolean; errors: string[]; }
export interface ProductConfigurationSummary {
  productId: number;
  fdProductId: number | null;
  productCode: string;
  productName: string;
  family: ProductFamily;
  categoryCode: string;
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE';
  baseCurrencyCode: string;
  currencyCodes: string[];
  revision: number;
}
export interface PricingPreview {
  status: 'SUPPORTED' | 'INCOMPLETE' | 'UNSUPPORTED';
  family: ProductFamily;
  currencyCode: string;
  fundingCurrencyCode: string | null;
  destinationCurrencyCode: string | null;
  calculationMethod: string | null;
  values: Record<string, number>;
  messages: string[];
}

@Injectable({ providedIn: 'root' })
export class ProductConfigurationAdminService {
  constructor(
    private readonly http: HttpClient,
    @Inject(BACKEND_API_BASE_URL) private readonly backendUrl: string,
  ) {}

  metadata(categoryId?: number): Observable<ProductBuilderMetadata> {
    const params = categoryId == null ? undefined : new HttpParams().set('categoryId', categoryId);
    return this.http.get<ProductBuilderMetadata>(this.path('/api/v1/product-configurations/metadata'), { params });
  }

  list(): Observable<ProductConfigurationSummary[]> {
    return this.http.get<ProductConfigurationSummary[]>(this.path('/api/v1/product-configurations'));
  }

  validateIdentity(request: { productName: string; description: string; effectiveFrom: string | null; effectiveTo: string | null }): Observable<Record<string,string>> {
    return this.http.post<Record<string,string>>(this.path('/api/v1/product-configurations/validate-identity'), request);
  }

  validate(request: ProductConfigurationRequest): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(this.path('/api/v1/product-configurations/validate'), request);
  }

  preview(request: ProductConfigurationRequest, facts: Record<string, string>): Observable<PricingPreview> {
    return this.http.post<PricingPreview>(this.path('/api/v1/product-configurations/preview'), {
      configuration: request,
      facts,
    });
  }

  create(request: ProductConfigurationRequest): Observable<ProductConfigurationSummary> {
    return this.http.post<ProductConfigurationSummary>(this.path('/api/v1/product-configurations'), request);
  }

  updateDraft(productId: number, request: ProductConfigurationRequest): Observable<ProductConfigurationSummary> {
    return this.http.put<ProductConfigurationSummary>(this.path(`/api/v1/product-configurations/${productId}`), request);
  }

  deleteDraft(productId: number): Observable<void> {
    return this.http.delete<void>(this.path(`/api/v1/product-configurations/${productId}`));
  }

  activate(productId: number): Observable<ProductConfigurationSummary> {
    return this.http.post<ProductConfigurationSummary>(
      this.path(`/api/v1/product-configurations/${productId}/activate`), {},
    );
  }

  createSubcategory(parentCategoryId: number, categoryName: string): Observable<unknown> {
    return this.http.post(this.path('/api/v1/product-categories'), {
      parentCategoryId,
      categoryName: categoryName.trim(),
    });
  }

  private path(path: string): string {
    return `${this.backendUrl.replace(/\/$/, '')}${path}`;
  }
}
