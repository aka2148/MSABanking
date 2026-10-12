import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AttributeOption,
  CategoryOption,
  CurrencyOption,
  FactOption,
  PricingPreview,
  ProductBuilderMetadata,
  ProductConfigurationAdminService,
  ProductConfigurationRequest,
  ProductConfigurationSummary,
  ProductFamily,
} from '../../../core/services/product-configuration-admin.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

interface CurrencyDraft { currencyCode: string; minimumAmount: number | null; maximumAmount: number | null; }
interface PricingDraft { currencyCode: string; valueFrom: number | null; valueTo: number | null; ratePercent: number | null; }
interface EligibilityDraft {
  name: string; fact: string; dataType: string; operator: string;
  valueFrom: string; valueTo: string; valueSet: string; combineLogic: 'AND' | 'OR';
}
interface ChargeDraft {
  chargeCode: string; calculationBasis: 'FIXED' | 'PERCENTAGE'; fact: string;
  valueFrom: number | null; valueTo: number | null; value: number | null;
  currencyCode: string; floor: number | null; cap: number | null;
}

@Component({
  selector: 'app-product-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, GlassShell],
  templateUrl: './product-builder.html',
  styleUrl: './product-builder.scss',
})
export class ProductBuilder implements OnInit {
  metadata: ProductBuilderMetadata | null = null;
  products: ProductConfigurationSummary[] = [];
  categoryId: number | null = null;
  productCode = '';
  productName = '';
  description = '';
  effectiveFrom = '';
  effectiveTo = '';
  baseCurrencyCode = 'INR';
  currencyDrafts: CurrencyDraft[] = [];
  pricingDrafts: PricingDraft[] = [];
  attributeValues: Record<number, string> = {};
  eligibilityRules: EligibilityDraft[] = [];
  charges: ChargeDraft[] = [];
  roleSelections: Record<number, boolean> = {};
  previewFacts: Record<string, string> = {
    AMOUNT: '100000', TENURE_MONTHS: '12', BALANCE: '50000', TRANSFER_AMOUNT: '100000',
    REFERENCE_RATE: '83.25', CURRENCY: 'INR', FUNDING_CURRENCY: 'INR', DESTINATION_CURRENCY: 'USD',
    REFERENCE_RATE_DIRECTION: 'FUNDING_PER_DESTINATION', FOREX_AMOUNT_MODE: 'CONVERSION_PRINCIPAL',
  };
  previewResult: PricingPreview | null = null;
  validationErrors: string[] = [];
  createdProduct: ProductConfigurationSummary | null = null;
  newCategoryCode = '';
  newCategoryName = '';
  loading = true;
  saving = false;
  previewing = false;
  activatingId: number | null = null;
  errorMessage = '';
  successMessage = '';

  constructor(private readonly service: ProductConfigurationAdminService) {}

  get selectedCategory(): CategoryOption | undefined {
    return this.metadata?.categories.find((category) => category.id === this.categoryId);
  }

  get family(): ProductFamily { return this.selectedCategory?.family ?? 'UNKNOWN'; }
  get attributes(): AttributeOption[] { return this.metadata?.attributes ?? []; }
  get currencies(): CurrencyOption[] { return this.metadata?.currencies ?? []; }
  get executableFacts(): FactOption[] { return this.metadata?.facts.filter((fact) => fact.executable) ?? []; }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.service.metadata(this.categoryId ?? undefined).subscribe({
      next: (metadata) => {
        this.metadata = metadata;
        if (this.categoryId == null) {
          this.categoryId = metadata.categories.find((category) => category.family === 'LOAN')?.id
            ?? metadata.categories[0]?.id ?? null;
          if (this.categoryId != null) this.categoryChanged();
        } else {
          this.initializeAttributes();
        }
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not load product-builder metadata.');
        this.loading = false;
      },
    });
    this.service.list().subscribe({
      next: (products) => { this.products = products; },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not load products.'); },
    });
  }

  categoryChanged(): void {
    if (this.categoryId == null) return;
    this.service.metadata(this.categoryId).subscribe({
      next: (metadata) => {
        this.metadata = metadata;
        this.initializeAttributes();
        this.initializeFamilyDefaults();
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not load category fields.'); },
    });
  }

  currencySelected(code: string): boolean { return this.currencyDrafts.some((draft) => draft.currencyCode === code); }

  toggleCurrency(currency: CurrencyOption): void {
    const index = this.currencyDrafts.findIndex((draft) => draft.currencyCode === currency.code);
    if (index >= 0) {
      if (this.currencyDrafts.length === 1) return;
      this.currencyDrafts.splice(index, 1);
      this.pricingDrafts = this.pricingDrafts.filter((band) => band.currencyCode !== currency.code);
      if (this.baseCurrencyCode === currency.code) this.baseCurrencyCode = this.currencyDrafts[0].currencyCode;
    } else {
      this.currencyDrafts.push({ currencyCode: currency.code, minimumAmount: null, maximumAmount: null });
      if (!(this.family === 'FOREX' && currency.code === this.baseCurrencyCode)) this.addPricingBand(currency.code);
    }
    this.syncForexPreviewCurrencies();
  }

  baseCurrencyChanged(): void {
    this.pricingDrafts = this.pricingDrafts.filter((band) => band.currencyCode !== this.baseCurrencyCode);
    this.previewCurrencyCodes().forEach((code) => {
      if (!this.pricingDrafts.some((band) => band.currencyCode === code)) this.addPricingBand(code);
    });
    this.charges.forEach((charge) => charge.currencyCode = this.baseCurrencyCode);
    this.syncForexPreviewCurrencies();
  }

  addPricingBand(currencyCode?: string): void {
    const applicable = currencyCode ?? this.previewCurrencyCodes()[0] ?? this.baseCurrencyCode;
    const previous = [...this.pricingDrafts].reverse().find((band) => band.currencyCode === applicable);
    this.pricingDrafts.push({
      currencyCode: applicable,
      valueFrom: previous?.valueTo == null ? null : previous.valueTo + 1,
      valueTo: previous?.valueTo == null ? null : previous.valueTo + 12,
      ratePercent: null,
    });
  }

  removePricingBand(index: number): void { if (this.pricingDrafts.length > 1) this.pricingDrafts.splice(index, 1); }

  addEligibilityRule(): void {
    const fact = this.executableFacts[0];
    this.eligibilityRules.push({
      name: `Eligibility rule ${this.eligibilityRules.length + 1}`,
      fact: fact?.code ?? 'KYC_STATUS', dataType: fact?.dataType ?? 'STRING',
      operator: fact?.operators[0] ?? 'EQ', valueFrom: '', valueTo: '', valueSet: '', combineLogic: 'AND',
    });
  }

  eligibilityFactChanged(rule: EligibilityDraft): void {
    const fact = this.metadata?.facts.find((option) => option.code === rule.fact);
    if (!fact) return;
    rule.dataType = fact.dataType;
    rule.operator = fact.operators[0];
  }

  addCharge(): void {
    this.charges.push({
      chargeCode: '', calculationBasis: 'FIXED', fact: this.pricingFact(),
      valueFrom: 0, valueTo: null, value: null, currencyCode: this.baseCurrencyCode,
      floor: null, cap: null,
    });
  }

  createSubcategory(): void {
    if (this.categoryId == null || !this.newCategoryCode.trim() || !this.newCategoryName.trim()) return;
    this.service.createSubcategory(this.categoryId, this.newCategoryCode, this.newCategoryName).subscribe({
      next: () => {
        this.successMessage = 'Product subcategory created. It inherits the selected family attributes.';
        this.newCategoryCode = '';
        this.newCategoryName = '';
        this.load();
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not create subcategory.'); },
    });
  }

  runPreview(form: NgForm): void {
    this.clearMessages();
    const request = this.buildRequest(form);
    if (!request) return;
    this.syncForexPreviewCurrencies();
    this.previewing = true;
    this.service.preview(request, this.previewFacts).subscribe({
      next: (result) => { this.previewResult = result; this.previewing = false; },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not calculate this preview.');
        this.previewing = false;
      },
    });
  }

  saveDraft(form: NgForm): void {
    this.clearMessages();
    const request = this.buildRequest(form);
    if (!request) return;
    this.saving = true;
    this.service.validate(request).subscribe({
      next: (validation) => {
        this.validationErrors = validation.errors;
        if (!validation.valid) { this.saving = false; return; }
        this.service.create(request).subscribe({
          next: (product) => {
            this.createdProduct = product;
            this.products = [product, ...this.products.filter((item) => item.productId !== product.productId)];
            this.successMessage = 'Complete configuration saved transactionally as a draft.';
            this.saving = false;
          },
          error: (error: HttpErrorResponse) => {
            this.errorMessage = this.errorText(error, 'Could not save this product configuration.');
            this.saving = false;
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not validate this configuration.');
        this.saving = false;
      },
    });
  }

  activate(product: ProductConfigurationSummary): void {
    this.clearMessages();
    this.activatingId = product.productId;
    this.service.activate(product.productId).subscribe({
      next: (active) => {
        this.products = this.products.map((item) => item.productId === active.productId ? active : item);
        if (this.createdProduct?.productId === active.productId) this.createdProduct = active;
        this.successMessage = `${active.productName} is active at revision ${active.revision}.`;
        this.activatingId = null;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Activation validation failed.');
        this.activatingId = null;
      },
    });
  }

  currencyStep(code: string): string {
    const decimals = this.currencies.find((currency) => currency.code === code)?.decimalPlaces ?? 2;
    return (1 / Math.pow(10, decimals)).toFixed(decimals);
  }

  pricingLabel(): string {
    return this.family === 'CASA' ? 'Balance band' : this.family === 'FOREX' ? 'Transfer amount band' : 'Tenure band (months)';
  }

  previewCurrencyCodes(): string[] {
    const codes = this.currencyDrafts.map((draft) => draft.currencyCode);
    return this.family === 'FOREX' ? codes.filter((code) => code !== this.baseCurrencyCode) : codes;
  }

  private initializeAttributes(): void {
    this.attributeValues = Object.fromEntries(this.attributes.map((attribute) => [attribute.id, attribute.defaultValue ?? '']));
  }

  private initializeFamilyDefaults(): void {
    const codes = this.family === 'FOREX' ? ['INR', 'USD', 'KWD'] : ['INR'];
    this.currencyDrafts = codes.filter((code) => this.currencies.some((currency) => currency.code === code))
      .map((currencyCode) => ({ currencyCode, minimumAmount: null, maximumAmount: null }));
    if (this.currencyDrafts.length === 0 && this.currencies[0]) {
      this.currencyDrafts = [{ currencyCode: this.currencies[0].code, minimumAmount: null, maximumAmount: null }];
    }
    this.baseCurrencyCode = this.currencyDrafts.find((draft) => draft.currencyCode === 'INR')?.currencyCode
      ?? this.currencyDrafts[0]?.currencyCode ?? '';
    this.pricingDrafts = [];
    this.previewCurrencyCodes().forEach((code) => this.addPricingBand(code));
    this.syncForexPreviewCurrencies();
    this.eligibilityRules = [];
    this.charges = [];
    this.previewResult = null;
    this.createdProduct = null;
  }

  private buildRequest(form: NgForm): ProductConfigurationRequest | null {
    if (form.invalid || this.categoryId == null || this.currencyDrafts.length === 0 || this.pricingDrafts.length === 0) {
      form.control.markAllAsTouched();
      this.errorMessage = 'Complete the required product, currency and pricing fields.';
      return null;
    }
    return {
      productCode: this.productCode.trim().toUpperCase(), productName: this.productName.trim(),
      categoryId: this.categoryId, description: this.description.trim(),
      effectiveFrom: this.effectiveFrom || null, effectiveTo: this.effectiveTo || null,
      baseCurrencyCode: this.baseCurrencyCode,
      currencies: this.currencyDrafts.map((draft) => ({
        currencyCode: draft.currencyCode, minimumAmount: draft.minimumAmount!, maximumAmount: draft.maximumAmount!,
      })),
      attributes: this.attributes.map((attribute) => ({ definitionId: attribute.id, value: this.attributeValues[attribute.id] ?? '' })),
      eligibilityRules: this.eligibilityRules,
      pricingBands: this.pricingDrafts.map((band) => ({
        currencyCode: band.currencyCode, fact: this.pricingFact(), valueFrom: band.valueFrom!, valueTo: band.valueTo!,
        rate: Number((band.ratePercent! / 100).toFixed(8)), outcomeType: this.outcomeType(),
        rateUnit: 'DECIMAL_FRACTION', calculationMethod: this.calculationMethod(),
      })),
      charges: this.charges.map((charge) => ({
        chargeCode: charge.chargeCode.trim().toUpperCase(), calculationBasis: charge.calculationBasis,
        slabs: [{
          fact: charge.fact, valueFrom: charge.valueFrom!, valueTo: charge.valueTo!,
          value: charge.calculationBasis === 'PERCENTAGE'
            ? Number((charge.value! / 100).toFixed(8)) : charge.value!,
          unit: charge.calculationBasis === 'PERCENTAGE' ? 'DECIMAL_FRACTION' : 'FIXED_AMOUNT',
          currencyCode: charge.currencyCode, floor: charge.floor, cap: charge.cap,
        }],
      })),
      roleConstraints: Object.entries(this.roleSelections).filter(([, selected]) => selected).map(([id]) => ({
        roleTypeId: Number(id), mandatory: true, minimumCount: 1, maximumCount: 1,
      })),
    };
  }

  private syncForexPreviewCurrencies(): void {
    const destinationCodes = this.previewCurrencyCodes();
    const currentDestination = String(this.previewFacts['DESTINATION_CURRENCY'] ?? '');
    const destination = destinationCodes.includes(currentDestination) ? currentDestination : destinationCodes[0] ?? '';
    this.previewFacts['CURRENCY'] = destination || this.baseCurrencyCode;
    this.previewFacts['FUNDING_CURRENCY'] = this.baseCurrencyCode;
    this.previewFacts['DESTINATION_CURRENCY'] = destination;
  }

  private pricingFact(): string {
    return this.family === 'CASA' ? 'BALANCE' : this.family === 'FOREX' ? 'TRANSFER_AMOUNT' : 'TENURE_MONTHS';
  }
  private outcomeType(): string {
    return this.family === 'CASA' ? 'CREDIT_INTEREST_RATE' : this.family === 'FOREX' ? 'MARKUP_RATE' : 'INTEREST_RATE';
  }
  private calculationMethod(): string {
    if (this.family === 'LOAN') return 'LOAN_REDUCING_BALANCE';
    if (this.family === 'CASA') return 'CASA_DAILY_BALANCE';
    if (this.family === 'FOREX') return 'FOREX_REFERENCE_MARKUP';
    return 'FD_QUARTERLY';
  }
  private clearMessages(): void {
    this.errorMessage = ''; this.successMessage = ''; this.validationErrors = []; this.previewResult = null;
  }
  private errorText(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) return 'Your session expired. Sign in again with a staff account.';
    if (error.status === 403) return 'Product setup requires the BANK_OFFICER or ADMIN role.';
    const message = error.error?.message ?? error.error?.error;
    return typeof message === 'string' && message ? message : fallback;
  }
}
