import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import {
  CurrencyAdminOption,
  FdOfferAdmin,
  FdOfferCreateRequest,
  FdProductAdminService,
  ProductCategoryOption,
} from '../../../core/services/fd-product-admin.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

interface RateBandDraft {
  durationMonths: 6 | 12 | 18 | 24 | 30;
  annualRatePercent: number | null;
}

interface CurrencyScheduleDraft {
  currencyCode: string;
  decimalPlaces: number;
  minimumAmount: string;
  maximumAmount: string;
  rateBands: RateBandDraft[];
}

interface ProductTranslationDraft { locale: string; productName: string; description: string; }
interface ProductEditDraft { productName: string; description: string; }

@Component({
  selector: 'app-fd-products-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, WorkspaceShell],
  templateUrl: './fd-products.html',
  styleUrl: './fd-products.scss',
})
export class FdProductsAdmin implements OnInit {
  private readonly bandDurations = [6, 12, 18, 24, 30] as const;
  categories: ProductCategoryOption[] = [];
  currencies: CurrencyAdminOption[] = [];
  offers: FdOfferAdmin[] = [];
  categoryId: number | null = null;
  productCode = '';
  productName = '';
  description = '';
  currencySchedules: CurrencyScheduleDraft[] = [];
  selectedCurrencyCode = '';
  currencyToAdd = '';
  createdOffer: FdOfferAdmin | null = null;
  loading = true;
  saving = false;
  activatingId: number | null = null;
  editingTranslationId: number | null = null;
  savingTranslationId: number | null = null;
  translationDrafts: Record<number, ProductTranslationDraft> = {};
  editingProductId: number | null = null;
  savingProductId: number | null = null;
  productEditDrafts: Record<number, ProductEditDraft> = {};
  errorMessage = '';
  successMessage = '';
  offerStep: 1 | 2 | 3 = 1;
  offerPage = 0;
  readonly offerPageSize = 8;

  constructor(private readonly service: FdProductAdminService, private readonly auth: AuthService) {}

  get sessionRoleLabel(): string {
    return (this.auth.currentRole() ?? 'UNKNOWN').replace(/^ROLE_/, '').replaceAll('_', ' ');
  }

  get listedOffers(): FdOfferAdmin[] {
    return this.offers.filter((offer) => offer.fd_product_id !== this.createdOffer?.fd_product_id);
  }

  get visibleOffers(): FdOfferAdmin[] {
    const start = this.offerPage * this.offerPageSize;
    return this.listedOffers.slice(start, start + this.offerPageSize);
  }

  get offerPageCount(): number { return Math.max(1, Math.ceil(this.listedOffers.length / this.offerPageSize)); }

  get selectedSchedule(): CurrencyScheduleDraft | undefined {
    return this.currencySchedules.find((schedule) => schedule.currencyCode === this.selectedCurrencyCode);
  }

  get availableCurrencies(): CurrencyAdminOption[] {
    return this.currencies.filter((currency) => !this.currencySchedules.some((schedule) => schedule.currencyCode === currency.isoCode));
  }

  get allSchedulesValid(): boolean {
    return this.currencySchedules.length > 0 && this.currencySchedules.every((schedule) => this.scheduleIsValid(schedule));
  }

  categoryName(): string {
    return this.categories.find((category) => category.categoryId === this.categoryId)?.categoryName ?? 'Select a category';
  }

  nextOfferStep(form: NgForm): void {
    this.errorMessage = '';
    if (this.offerStep === 1) {
      const required = ['productCode', 'productName', 'categoryId'];
      if (required.some((name) => form.controls[name]?.invalid)) {
        required.forEach((name) => form.controls[name]?.markAsTouched());
        this.errorMessage = 'Enter a product code and name, then select a category.';
        return;
      }
      const existing = this.offers.find((offer) => offer.product_code.toUpperCase() === this.productCode.trim().toUpperCase());
      if (existing) {
        this.errorMessage = `Product code ${existing.product_code} already exists. Choose a different code or edit the existing product below.`;
        return;
      }
      this.offerStep = 2;
      return;
    }
    if (!this.allSchedulesValid) {
      this.errorMessage = 'Add a currency schedule and complete its limits and five term rates before continuing.';
      return;
    }
    this.offerStep = 3;
  }

  previousOfferStep(): void {
    this.errorMessage = '';
    if (this.offerStep > 1) this.offerStep = (this.offerStep - 1) as 1 | 2 | 3;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    forkJoin({ categories: this.service.listCategories(), currencies: this.service.listCurrencies(), offers: this.service.listOffers() }).subscribe({
      next: ({ categories, currencies, offers }) => {
        this.categories = this.fixedDepositCategories(categories);
        this.currencies = currencies.filter((currency) => currency.active);
        this.offers = offers;
        this.offerPage = 0;
        this.categoryId = this.categories.find((category) => category.categoryCode === 'FIXED_DEPOSIT')?.categoryId ?? this.categories[0]?.categoryId ?? null;
        this.resetSchedules();
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not load product categories, currencies, or staff offers.'); this.loading = false; },
    });
  }

  selectCurrency(currencyCode: string): void {
    this.selectedCurrencyCode = currencyCode;
  }

  addCurrencySchedule(): void {
    const currency = this.availableCurrencies.find((item) => item.isoCode === this.currencyToAdd);
    if (!currency) return;
    this.currencySchedules.push(this.newSchedule(currency));
    this.selectedCurrencyCode = currency.isoCode;
    this.currencyToAdd = this.availableCurrencies[0]?.isoCode ?? '';
  }

  removeCurrencySchedule(schedule: CurrencyScheduleDraft): void {
    this.currencySchedules = this.currencySchedules.filter((item) => item.currencyCode !== schedule.currencyCode);
    this.selectedCurrencyCode = this.currencySchedules[0]?.currencyCode ?? '';
  }

  unformatAmount(schedule: CurrencyScheduleDraft, field: 'minimumAmount' | 'maximumAmount'): void {
    const amount = this.parseAmount(schedule[field], schedule.decimalPlaces);
    if (amount !== null) schedule[field] = String(amount);
  }

  formatAmount(schedule: CurrencyScheduleDraft, field: 'minimumAmount' | 'maximumAmount'): void {
    const amount = this.parseAmount(schedule[field], schedule.decimalPlaces);
    if (amount === null) return;
    const locale = typeof navigator !== 'undefined' ? navigator.language : 'en-US';
    schedule[field] = new Intl.NumberFormat(locale, {
      style: 'currency', currency: schedule.currencyCode, minimumFractionDigits: schedule.decimalPlaces, maximumFractionDigits: schedule.decimalPlaces,
    }).format(amount);
  }

  canSubmit(form: NgForm): boolean {
    return form.valid === true && this.categoryId !== null && !this.saving && !this.createdOffer
      && this.currencySchedules.length > 0 && this.currencySchedules.every((schedule) => this.scheduleIsValid(schedule));
  }

  createDraft(form: NgForm): void {
    this.successMessage = '';
    this.errorMessage = '';
    if (!this.canSubmit(form)) {
      form.control.markAllAsTouched();
      this.errorMessage = 'Add at least one currency schedule, then complete its amount limits and five rate bands before saving.';
      return;
    }
    if (this.offers.some((offer) => offer.product_code.toUpperCase() === this.productCode.trim().toUpperCase())) {
      this.errorMessage = `Product code ${this.productCode.trim().toUpperCase()} already exists. Choose a different code or edit the existing product below.`;
      this.offerStep = 1;
      return;
    }

    const request: FdOfferCreateRequest = {
      productCode: this.productCode.trim().toUpperCase(),
      productName: this.productName.trim(),
      categoryId: this.categoryId!,
      description: this.description.trim(),
      currencies: this.currencySchedules.map((schedule) => ({
        currencyCode: schedule.currencyCode,
        minimumAmount: this.parseAmount(schedule.minimumAmount, schedule.decimalPlaces)!,
        maximumAmount: this.parseAmount(schedule.maximumAmount, schedule.decimalPlaces)!,
        rateBands: [...schedule.rateBands]
          .sort((left, right) => left.durationMonths - right.durationMonths)
          .map((band) => ({
            minimumTenureMonths: band.durationMonths - 5,
            maximumTenureMonths: band.durationMonths,
            annualRate: band.annualRatePercent! / 100,
          })),
      })),
    };

    this.saving = true;
    this.service.createOffer(request).subscribe({
      next: (offer) => {
        this.createdOffer = offer;
        this.offers = [offer, ...this.offers.filter((existing) => existing.fd_product_id !== offer.fd_product_id)];
        this.successMessage = 'Draft saved. Review the schedule and activate it to show it to customers.';
        this.saving = false;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not save this FD product.');
        this.saving = false;
      },
    });
  }

  activate(offer: FdOfferAdmin): void {
    this.activatingId = offer.fd_product_id;
    this.errorMessage = '';
    this.successMessage = '';
    this.service.activateOffer(offer.fd_product_id).subscribe({
      next: (active) => {
        this.offers = this.offers.map((existing) => existing.fd_product_id === active.fd_product_id ? active : existing);
        if (this.createdOffer?.fd_product_id === active.fd_product_id) this.createdOffer = active;
        this.successMessage = `${active.product_name} is active and available to customers.`;
        this.activatingId = null;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'This product could not be activated. Check both schedules and retry.');
        this.activatingId = null;
      },
    });
  }

  deactivate(offer: FdOfferAdmin): void {
    this.activatingId = offer.fd_product_id;
    this.errorMessage = '';
    this.successMessage = '';
    this.service.deactivateOffer(offer.catalog_product_id).subscribe({
      next: (inactive) => {
        this.offers = this.offers.map((existing) => existing.fd_product_id === inactive.fd_product_id ? inactive : existing);
        this.successMessage = `${inactive.product_name} is no longer available for new bookings.`;
        this.activatingId = null;
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'This product could not be deactivated.'); this.activatingId = null; },
    });
  }

  toggleTranslation(offer: FdOfferAdmin): void {
    if (this.editingTranslationId === offer.fd_product_id) { this.editingTranslationId = null; return; }
    this.editingTranslationId = offer.fd_product_id;
    this.translationDrafts[offer.fd_product_id] ??= { locale: 'es', productName: offer.product_name, description: '' };
  }

  toggleProductEditor(offer: FdOfferAdmin): void {
    if (this.editingProductId === offer.fd_product_id) { this.editingProductId = null; return; }
    this.editingProductId = offer.fd_product_id;
    this.service.getProduct(offer.catalog_product_id).subscribe({
      next: (product) => this.productEditDrafts[offer.fd_product_id] = {
        productName: product.productName,
        description: product.description ?? '',
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not load this product for editing.');
        this.editingProductId = null;
      },
    });
  }

  saveProduct(offer: FdOfferAdmin): void {
    const draft = this.productEditDrafts[offer.fd_product_id];
    if (!draft?.productName.trim()) return;
    this.savingProductId = offer.fd_product_id;
    this.errorMessage = '';
    this.service.updateOffer(offer.catalog_product_id, {
      productName: draft.productName.trim(), description: draft.description.trim(),
    }).subscribe({
      next: (updated) => {
        this.offers = this.offers.map((existing) => existing.fd_product_id === updated.fd_product_id ? updated : existing);
        this.successMessage = `${updated.product_name} updated and its catalog snapshot sent to FD.`;
        this.savingProductId = null;
        this.editingProductId = null;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not update this product.');
        this.savingProductId = null;
      },
    });
  }

  saveTranslation(offer: FdOfferAdmin): void {
    const draft = this.translationDrafts[offer.fd_product_id];
    if (!draft || !draft.locale.trim() || !draft.productName.trim()) return;
    this.savingTranslationId = offer.fd_product_id;
    this.errorMessage = '';
    this.service.saveTranslation(offer.catalog_product_id, draft.locale.trim(), draft.productName.trim(), draft.description.trim()).subscribe({
      next: () => { this.successMessage = `Translation saved for ${draft.locale}.`; this.savingTranslationId = null; },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not save this product translation.'); this.savingTranslationId = null; },
    });
  }

  resetForm(form: NgForm): void {
    form.resetForm();
    this.offerStep = 1;
    this.productCode = '';
    this.productName = '';
    this.description = '';
    this.createdOffer = null;
    this.categoryId = this.categories.find((category) => category.categoryCode === 'FIXED_DEPOSIT')?.categoryId
      ?? this.categories[0]?.categoryId ?? null;
    this.resetSchedules();
    this.successMessage = '';
    this.errorMessage = '';
  }

  scheduleIsValid(schedule: CurrencyScheduleDraft): boolean {
    const minimum = this.parseAmount(schedule.minimumAmount, schedule.decimalPlaces);
    const maximum = this.parseAmount(schedule.maximumAmount, schedule.decimalPlaces);
    if (!this.positive(minimum) || !this.positive(maximum) || minimum! > maximum!
        || schedule.rateBands.length !== this.bandDurations.length) return false;
    return schedule.rateBands.every((band, index) => {
      return band.durationMonths === this.bandDurations[index]
        && band.annualRatePercent !== null
        && Number.isFinite(band.annualRatePercent)
        && band.annualRatePercent >= 0
        && band.annualRatePercent <= 100
        && Math.round(band.annualRatePercent * 100) === band.annualRatePercent * 100;
    });
  }

  private positive(value: number | null): boolean {
    return value !== null && Number.isFinite(value) && value > 0;
  }

  private parseAmount(value: string, scale: number): number | null {
    const normalized = value.replace(/[^\d.-]/g, '');
    const pattern = scale === 0 ? /^\d+$/ : new RegExp(`^\\d+(\\.\\d{0,${scale}})?$`);
    if (!normalized || !pattern.test(normalized)) return null;
    const amount = Number(normalized);
    return Number.isFinite(amount) ? amount : null;
  }

  private newSchedule(currency: CurrencyAdminOption): CurrencyScheduleDraft {
    return {
      currencyCode: currency.isoCode,
      decimalPlaces: currency.decimalPlaces,
      minimumAmount: '',
      maximumAmount: '',
      rateBands: this.bandDurations.map((durationMonths) => ({ durationMonths, annualRatePercent: null })),
    };
  }

  private resetSchedules(): void {
    const preferred = ['USD', 'INR'].map((code) => this.currencies.find((currency) => currency.isoCode === code)).filter((currency): currency is CurrencyAdminOption => !!currency);
    const initial = preferred.length ? preferred : this.currencies.slice(0, 1);
    this.currencySchedules = initial.map((currency) => this.newSchedule(currency));
    this.selectedCurrencyCode = this.currencySchedules[0]?.currencyCode ?? '';
    this.currencyToAdd = this.availableCurrencies[0]?.isoCode ?? '';
  }

  private errorText(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) return 'Your session expired. Sign in again with a staff account.';
    if (error.status === 403) {
      const endpoint = error.url?.replace(/^https?:\/\/[^/]+/, '').split('?')[0] || 'the product service';
      return `The product service denied ${endpoint} for this session (${this.auth.currentRole() ?? 'unknown role'}). Sign out and back in after confirming your account has BANK_OFFICER or ADMIN access.`;
    }
    const message = error.error?.message ?? error.error?.error;
    return typeof message === 'string' && message ? message : fallback;
  }

  private fixedDepositCategories(categories: ProductCategoryOption[]): ProductCategoryOption[] {
    const byId = new Map(categories.map((category) => [category.categoryId, category]));
    return categories.filter((category) => {
      const visited = new Set<number>();
      let current: ProductCategoryOption | undefined = category;
      while (current && !visited.has(current.categoryId)) {
        visited.add(current.categoryId);
        const code = current.categoryCode.trim().toUpperCase();
        if (code === 'FIXED_DEPOSIT' || code === 'FD' || code.includes('FD')) return true;
        current = current.parentCategoryId == null ? undefined : byId.get(current.parentCategoryId);
      }
      return false;
    });
  }
}
