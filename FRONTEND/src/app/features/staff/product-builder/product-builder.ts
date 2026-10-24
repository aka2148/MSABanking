import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
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
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';
import { FD_CURRENCY_DEFAULTS, PRODUCT_TEMPLATES, ProductTemplate } from './product-templates';

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
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  templateUrl: './product-builder.html',
  styleUrl: './product-builder.scss',
})
export class ProductBuilder implements OnInit, OnDestroy {
  private readonly draftStorageKey = 'product-builder-draft-v1';
  private restoringDraft = false;
  readonly productSteps = ['Product family', 'Identity', 'Terms', 'Money', 'Eligibility', 'Pricing', 'Review', 'Publish'];
  readonly templates = PRODUCT_TEMPLATES;
  selectedTemplateKey: string | null = null;
  productStep = 1;
  catalogPage = 0;
  readonly catalogPageSize = 8;
  metadata: ProductBuilderMetadata | null = null;
  products: ProductConfigurationSummary[] = [];
  categoryId: number | null = null;
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
    AMOUNT: '100000', TENURE_MONTHS: '12', BALANCE_AMOUNT: '50000', TRANSFER_AMOUNT: '100000',
    REFERENCE_RATE: '83.25', CURRENCY: 'INR', FUNDING_CURRENCY: 'INR', DESTINATION_CURRENCY: 'USD',
    REFERENCE_RATE_DIRECTION: 'FUNDING_PER_DESTINATION', FOREX_AMOUNT_MODE: 'CONVERSION_PRINCIPAL',
    KYC_STATUS: 'VERIFIED', RISK_CATEGORY: 'LOW', VERIFIED_IDENTITY: 'true',
  };
  previewResult: PricingPreview | null = null;
  validationErrors: string[] = [];
  createdProduct: ProductConfigurationSummary | null = null;
  newCategoryName = '';
  catalogLoading = true;
  catalogError = false;
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

  get today(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  get family(): ProductFamily { return this.selectedCategory?.family ?? 'UNKNOWN'; }
  get familyLabel(): string {
    return ({ FIXED_DEPOSIT: 'Fixed deposit', LOAN: 'Loan', CASA: 'Savings and current accounts', FOREX: 'Foreign exchange' } as Record<string, string>)[this.family] ?? this.family;
  }
  get selectedTemplate(): ProductTemplate | undefined { return this.templates.find((template) => template.key === this.selectedTemplateKey); }
  get attributes(): AttributeOption[] { return this.metadata?.attributes ?? []; }
  get currencies(): CurrencyOption[] { return this.metadata?.currencies ?? []; }
  get executableFacts(): FactOption[] { return this.metadata?.facts.filter((fact) => fact.executable) ?? []; }
  get visibleProducts(): ProductConfigurationSummary[] {
    const start = this.catalogPage * this.catalogPageSize;
    return this.products.slice(start, start + this.catalogPageSize);
  }
  get catalogPageCount(): number { return Math.max(1, Math.ceil(this.products.length / this.catalogPageSize)); }

  identityErrors: Record<string,string> = {};
  validatingIdentity = false;
  identityEdited(field: string): void { delete this.identityErrors[field]; }
  factOption(rule: EligibilityDraft): FactOption | undefined { return this.metadata?.facts.find(fact => fact.code === rule.fact); }

  ruleName(rule: EligibilityDraft): string { return `${rule.fact} ${rule.operator} ${rule.operator === 'IN' ? rule.valueSet : rule.operator === 'BETWEEN_INCLUSIVE' ? `${rule.valueFrom} - ${rule.valueTo}` : rule.valueFrom}`.trim().slice(0, 150); }
  private selectPrimaryHolder(): void { this.metadata?.roles.filter(role => role.code === 'PRIMARY_HOLDER').forEach(role => this.roleSelections[role.id] = true); }

  ngOnInit(): void { this.restoreDraft(); this.load(); }

  ngOnDestroy(): void { this.persistDraft(); }

  private restoreDraft(): void {
    try {
      const raw = sessionStorage.getItem(this.draftStorageKey);
      if (!raw) return;
      const draft = JSON.parse(raw) as Record<string, unknown>;
      const fields = ['selectedTemplateKey', 'productStep', 'categoryId', 'productName', 'description', 'effectiveFrom',
        'effectiveTo', 'baseCurrencyCode', 'currencyDrafts', 'pricingDrafts', 'attributeValues', 'eligibilityRules',
        'charges', 'roleSelections', 'previewFacts', 'createdProduct'];
      this.restoringDraft = true;
      for (const key of fields) if (key in draft) (this as unknown as Record<string, unknown>)[key] = draft[key];
      this.productStep = Math.min(this.productSteps.length, Math.max(1, Number(this.productStep) || 1));
    } catch {
      try { sessionStorage.removeItem(this.draftStorageKey); } catch { /* Browser storage can be disabled. */ }
    } finally {
      this.restoringDraft = false;
    }
  }

  persistDraft(): void {
    if (this.restoringDraft || this.loading || !this.metadata) return;
    try {
      const { selectedTemplateKey, productStep, categoryId, productName, description, effectiveFrom, effectiveTo,
        baseCurrencyCode, currencyDrafts, pricingDrafts, attributeValues, eligibilityRules, charges, roleSelections,
        previewFacts, createdProduct } = this;
      sessionStorage.setItem(this.draftStorageKey, JSON.stringify({ selectedTemplateKey, productStep, categoryId,
        productName, description, effectiveFrom, effectiveTo, baseCurrencyCode, currencyDrafts, pricingDrafts,
        attributeValues, eligibilityRules, charges, roleSelections, previewFacts, createdProduct }));
    } catch { /* The builder remains usable when browser storage is unavailable. */ }
  }

  load(): void {
    this.loading = true;
    this.catalogLoading = true;
    this.catalogError = false;
    this.errorMessage = '';
    this.service.metadata(this.categoryId ?? undefined).subscribe({
      next: (metadata) => {
        this.metadata = metadata;
        this.selectPrimaryHolder();
        if (this.categoryId == null) {
          this.categoryId = metadata.categories.find((category) => category.family === 'LOAN')?.id
            ?? metadata.categories[0]?.id ?? null;
          if (this.categoryId != null) this.categoryChanged();
        } else {
          this.initializeAttributes();
        }
        this.loading = false;
        this.persistDraft();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not load product-builder metadata.');
        this.loading = false;
      },
    });
    this.service.list().subscribe({
      next: (products) => { this.products = products; this.catalogPage = Math.min(this.catalogPage, this.catalogPageCount - 1); this.catalogLoading = false; },
      error: (error: HttpErrorResponse) => { this.catalogLoading = false; this.catalogError = true; this.errorMessage = this.errorText(error, 'Could not load products.'); },
    });
  }

  categoryChanged(): void {
    this.selectedTemplateKey = null;
    if (this.categoryId == null) return;
    const requestedCategoryId = this.categoryId;
    this.service.metadata(requestedCategoryId).subscribe({
      next: (metadata) => {
        if (this.categoryId !== requestedCategoryId || this.selectedTemplateKey) return;
        this.metadata = metadata;
        this.selectPrimaryHolder();
        this.initializeAttributes();
        this.initializeFamilyDefaults();
        this.persistDraft();
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not load category fields.'); },
    });
  }

  canUseTemplate(template: ProductTemplate): boolean {
    return !!this.metadata?.categories.some((category) => category.code === template.categoryCode && category.family === template.family)
      && template.currencies.every((currency) => this.currencies.some((option) => option.code === currency.code));
  }

  templateChanged(): void {
    const template = this.selectedTemplate;
    if (!template || !this.metadata) return;
    const category = this.metadata.categories.find((option) => option.code === template.categoryCode && option.family === template.family);
    if (!category || !this.canUseTemplate(template)) {
      this.errorMessage = 'This template requires a category or active currency that is not configured.';
      this.selectedTemplateKey = null;
      return;
    }
    this.categoryId = category.id;
    this.service.metadata(category.id).subscribe({
      next: (metadata) => {
        if (this.categoryId !== category.id || this.selectedTemplateKey !== template.key) return;
        const missing = Object.keys(template.attributes).filter((key) => !metadata.attributes.some((attribute) =>
          attribute.key === key && (!attribute.choices.length || attribute.choices.includes(template.attributes[key]))));
        if (missing.length) {
          this.errorMessage = `Template attributes are unavailable for this category: ${missing.join(', ')}`;
          this.selectedTemplateKey = null;
          return;
        }
        this.metadata = metadata;
        this.selectPrimaryHolder();
        this.initializeAttributes();
        this.initializeFamilyDefaults();
        this.applyTemplate(template);
        this.persistDraft();
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not load template fields.'); },
    });
  }

  private applyTemplate(template: ProductTemplate): void {
    this.clearMessages();
    this.identityErrors = {};
    this.productName = template.name;
    this.description = template.description;
    this.effectiveFrom = '';
    this.effectiveTo = '';
    this.attributeValues = Object.fromEntries(this.attributes.map((attribute) =>
      [attribute.id, template.attributes[attribute.key] ?? attribute.defaultValue ?? '']));
    this.currencyDrafts = template.currencies.map((currency) => ({
      currencyCode: currency.code, minimumAmount: currency.minimumAmount, maximumAmount: currency.maximumAmount,
    }));
    this.baseCurrencyCode = template.currencies[0].code;
    this.pricingDrafts = template.pricing.map((band) => ({
      currencyCode: band.currencyCode, valueFrom: band.from, valueTo: band.to, ratePercent: band.ratePercent,
    }));
    this.eligibilityRules = template.eligibility ? [{
      ...template.eligibility, valueTo: '', valueSet: '', combineLogic: 'AND',
    }] : [];
    this.charges = [];
    this.roleSelections = {};
    this.selectPrimaryHolder();
    this.previewFacts['AMOUNT'] = String(Math.max(template.currencies[0].minimumAmount, 1));
    this.previewFacts['TENURE_MONTHS'] = String(template.pricing[0].from);
    this.previewFacts['BALANCE_AMOUNT'] = String(Math.max(Number(template.attributes['MINIMUM_BALANCE'] ?? 0), template.pricing[0].from));
    this.previewFacts['TRANSFER_AMOUNT'] = String(template.pricing[0].from);
    if (template.eligibility) this.previewFacts[template.eligibility.fact] = template.eligibility.valueFrom;
    this.syncForexPreviewCurrencies();
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
    this.persistDraft();
  }

  pricingCurrencyChanged(band: PricingDraft, currencyCode: string): void {
    band.currencyCode = currencyCode;
    this.persistDraft();
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
    this.pricingDrafts.push({
      currencyCode: applicable,
      valueFrom: null,
      valueTo: null,
      ratePercent: null,
    });
    this.persistDraft();
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
    rule.valueFrom = ''; rule.valueTo = ''; rule.valueSet = '';
  }

  addCharge(): void {
    this.charges.push({
      chargeCode: '', calculationBasis: 'FIXED', fact: this.pricingFact(),
      valueFrom: 0, valueTo: null, value: null, currencyCode: this.baseCurrencyCode,
      floor: null, cap: null,
    });
  }

  createSubcategory(): void {
    if (this.categoryId == null || !this.newCategoryName.trim()) return;
    this.service.createSubcategory(this.categoryId, this.newCategoryName).subscribe({
      next: () => {
        this.successMessage = 'Product subcategory created. It inherits the selected family attributes.';
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

  nextProductStep(form: NgForm): void {
    if (this.validatingIdentity) return;
    const names = Object.keys(form.controls);
    const belongs = (name: string): boolean => {
      if (this.productStep === 1) return name === 'categoryId';
      if (this.productStep === 2) return name === 'productCode' || name === 'productName';
      if (this.productStep === 3) return name.startsWith('attribute-');
      if (this.productStep === 4) return name === 'baseCurrency' || name.startsWith('currency-');
      if (this.productStep === 5) return name.startsWith('eligibility-');
      if (this.productStep === 6) return name.startsWith('pricing-') || name.startsWith('charge-');
      return false;
    };
    const invalid = names.filter((name) => belongs(name) && form.controls[name].invalid);
    if (invalid.length) {
      invalid.forEach((name) => form.controls[name].markAsTouched());
      this.errorMessage = 'Complete the required fields in this step before continuing.';
      return;
    }
    this.clearMessages();
    if (this.productStep === 2) {
      if (!this.validateIdentityDates()) {
        form.controls['effectiveFrom']?.markAsTouched();
        form.controls['effectiveTo']?.markAsTouched();
        return;
      }
      const identity = { productName: this.productName, description: this.description, effectiveFrom: this.effectiveFrom || null, effectiveTo: this.effectiveTo || null };
      this.validatingIdentity = true;
      this.service.validateIdentity(identity).subscribe({
        next: errors => {
          this.validatingIdentity = false;
          if (identity.productName !== this.productName || identity.description !== this.description || identity.effectiveFrom !== (this.effectiveFrom || null) || identity.effectiveTo !== (this.effectiveTo || null)) return;
          this.identityErrors = errors;
          if (!Object.keys(errors).length) {
            this.productStep = this.attributes.length ? 3 : 4;
            this.persistDraft();
          }
        },
        error: (error: HttpErrorResponse) => { this.validatingIdentity = false; this.errorMessage = this.errorText(error, 'Could not validate this configuration.'); },
      });
      return;
    }
    this.productStep = Math.min(8, this.productStep + 1);
    this.persistDraft();
  }

  previousProductStep(form: NgForm): void {
    this.clearMessages();
    this.productStep = this.productStep === 4 && !this.attributes.length ? 2 : Math.max(1, this.productStep - 1);
    this.persistDraft();
  }

  saveDraft(form: NgForm): void {
    this.clearMessages();
    const request = this.buildRequest(form);
    if (!request) {
      this.productStep = this.firstInvalidStep(form);
      return;
    }
    this.saving = true;
    this.service.validate(request).subscribe({
      next: (validation) => {
        this.validationErrors = validation.errors;
        if (!validation.valid) { this.saving = false; return; }
        const saveRequest = this.createdProduct?.status === 'DRAFT'
          ? this.service.updateDraft(this.createdProduct.productId, request)
          : this.service.create(request);
        saveRequest.subscribe({
          next: (product) => {
            this.createdProduct = product;
            this.products = [product, ...this.products.filter((item) => item.productId !== product.productId)];
            this.catalogPage = 0;
            this.successMessage = 'Complete configuration saved transactionally as a draft.';
            this.saving = false;
            this.persistDraft();
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

  private firstInvalidStep(form: NgForm): number {
    const invalid = Object.entries(form.controls).filter(([, control]) => control.invalid).map(([name]) => name);
    if (invalid.includes('categoryId')) return 1;
    if (invalid.includes('productCode') || invalid.includes('productName')) return 2;
    if (invalid.some((name) => name.startsWith('attribute-'))) return 3;
    if (invalid.some((name) => name === 'baseCurrency' || name.startsWith('currency-'))) return 4;
    if (invalid.some((name) => name.startsWith('eligibility-'))) return 5;
    if (invalid.some((name) => name.startsWith('pricing-') || name.startsWith('charge-'))) return 6;
    return 7;
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

  validateCurrent(form: NgForm): void {
    this.clearMessages();
    const request = this.buildRequest(form);
    if (!request) return;
    this.saving = true;
    this.service.validate(request).subscribe({
      next: (result) => {
        this.saving = false;
        this.validationErrors = result.errors;
        if (result.valid) this.successMessage = 'Configuration is valid.';
      },
      error: (error: HttpErrorResponse) => { this.saving = false; this.errorMessage = this.errorText(error, 'Could not validate the current configuration.'); },
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
    this.attributeValues = Object.fromEntries(this.attributes.map((attribute) => [attribute.id,
      this.attributeValues[attribute.id] ?? attribute.defaultValue ?? '']));
  }

  private initializeFamilyDefaults(): void {
    const codes = this.family === 'FOREX' ? ['INR', 'USD', 'KWD'] : ['INR'];
    this.currencyDrafts = codes.filter((code) => this.currencies.some((currency) => currency.code === code))
      .map((currencyCode) => {
        const amount = this.family === 'FIXED_DEPOSIT' ? FD_CURRENCY_DEFAULTS.find((item) => item.code === currencyCode) : undefined;
        return { currencyCode, minimumAmount: amount?.minimumAmount ?? null, maximumAmount: amount?.maximumAmount ?? null };
      });
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
    if (!this.validateIdentityDates()) { this.productStep = 2; return null; }
    if (this.pricingDrafts.some((band) => band.ratePercent == null || !Number.isFinite(Number(band.ratePercent)))) {
      this.productStep = 6;
      this.errorMessage = 'Enter an annual rate for every pricing band before saving or previewing.';
      return null;
    }
    this.selectPrimaryHolder();
    if (form.invalid || this.categoryId == null || this.currencyDrafts.length === 0 || this.pricingDrafts.length === 0) {
      form.control.markAllAsTouched();
      this.errorMessage = 'Complete the required product, currency and pricing fields.';
      return null;
    }
    return {
      productCode: '', productName: this.productName.trim(),
      categoryId: this.categoryId, description: this.description.trim(),
      effectiveFrom: this.effectiveFrom || null, effectiveTo: this.effectiveTo || null,
      baseCurrencyCode: this.baseCurrencyCode,
      currencies: this.currencyDrafts.map((draft) => ({
        currencyCode: draft.currencyCode, minimumAmount: draft.minimumAmount!, maximumAmount: draft.maximumAmount!,
      })),
      attributes: this.attributes.map((attribute) => ({ definitionId: attribute.id, value: this.attributeValues[attribute.id] ?? '' })),
      eligibilityRules: this.eligibilityRules.map(rule => ({ ...rule, name: this.ruleName(rule) })),
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

  private validateIdentityDates(): boolean {
    this.identityErrors = { ...this.identityErrors };
    delete this.identityErrors['effectiveFrom'];
    delete this.identityErrors['effectiveTo'];
    if (!this.effectiveFrom) this.identityErrors['effectiveFrom'] = 'Effective-from date is required.';
    else if (this.effectiveFrom < this.today) this.identityErrors['effectiveFrom'] = 'Effective-from date must not be in the past.';
    if (!this.effectiveTo) this.identityErrors['effectiveTo'] = 'Effective-to date is required.';
    else if (this.effectiveTo < this.today) this.identityErrors['effectiveTo'] = 'Effective-to date must not be in the past.';
    else if (this.effectiveFrom && this.effectiveTo < this.effectiveFrom) this.identityErrors['effectiveTo'] = 'Effective-to date must be on or after effective-from date.';
    const valid = !this.identityErrors['effectiveFrom'] && !this.identityErrors['effectiveTo'];
    if (!valid) this.errorMessage = this.identityErrors['effectiveFrom'] || this.identityErrors['effectiveTo'];
    return valid;
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
    return this.family === 'CASA' ? 'BALANCE_AMOUNT' : this.family === 'FOREX' ? 'TRANSFER_AMOUNT' : 'TENURE_MONTHS';
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
