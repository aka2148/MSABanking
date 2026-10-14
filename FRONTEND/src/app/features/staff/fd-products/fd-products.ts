import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  FdOfferAdmin,
  FdOfferCreateRequest,
  FdProductAdminService,
  ProductCategoryOption,
} from '../../../core/services/fd-product-admin.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

interface RateBandDraft {
  durationMonths: 6 | 12 | 18 | 24 | 30;
  annualRatePercent: number | null;
}

interface CurrencyScheduleDraft {
  currencyCode: 'USD' | 'INR';
  minimumAmount: string;
  maximumAmount: string;
  rateBands: RateBandDraft[];
}

@Component({
  selector: 'app-fd-products-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, GlassShell],
  templateUrl: './fd-products.html',
  styleUrl: './fd-products.scss',
})
export class FdProductsAdmin implements OnInit {
  private readonly bandDurations = [6, 12, 18, 24, 30] as const;
  categories: ProductCategoryOption[] = [];
  offers: FdOfferAdmin[] = [];
  categoryId: number | null = null;
  productCode = '';
  productName = '';
  description = '';
  currencySchedules: CurrencyScheduleDraft[] = [this.newSchedule('USD'), this.newSchedule('INR')];
  selectedCurrencyCode: 'USD' | 'INR' = 'USD';
  createdOffer: FdOfferAdmin | null = null;
  loading = true;
  saving = false;
  activatingId: number | null = null;
  errorMessage = '';
  successMessage = '';

  constructor(private readonly service: FdProductAdminService) {}

  get listedOffers(): FdOfferAdmin[] {
    return this.offers.filter((offer) => offer.fd_product_id !== this.createdOffer?.fd_product_id);
  }

  get selectedSchedule(): CurrencyScheduleDraft | undefined {
    return this.currencySchedules.find((schedule) => schedule.currencyCode === this.selectedCurrencyCode);
  }

  get otherCurrencySchedule(): CurrencyScheduleDraft | undefined {
    return this.currencySchedules.find((schedule) => schedule.currencyCode !== this.selectedCurrencyCode);
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.service.listCategories().subscribe({
      next: (categories) => {
        this.categories = categories;
        this.categoryId = categories.find((category) => category.categoryCode === 'FIXED_DEPOSIT')?.categoryId
          ?? categories[0]?.categoryId ?? null;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not load product categories.');
      },
    });
    this.service.listOffers().subscribe({
      next: (offers) => { this.offers = offers; this.loading = false; },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.errorText(error, 'Could not load staff FD products.');
        this.loading = false;
      },
    });
  }

  selectCurrency(currencyCode: 'USD' | 'INR'): void {
    this.selectedCurrencyCode = currencyCode;
  }

  unformatAmount(schedule: CurrencyScheduleDraft, field: 'minimumAmount' | 'maximumAmount'): void {
    const amount = this.parseAmount(schedule[field]);
    if (amount !== null) schedule[field] = String(amount);
  }

  formatAmount(schedule: CurrencyScheduleDraft, field: 'minimumAmount' | 'maximumAmount'): void {
    const amount = this.parseAmount(schedule[field]);
    if (amount === null) return;
    const locale = schedule.currencyCode === 'INR' ? 'en-IN' : 'en-US';
    schedule[field] = new Intl.NumberFormat(locale, {
      style: 'currency', currency: schedule.currencyCode, minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(amount);
  }

  canSubmit(form: NgForm): boolean {
    return form.valid === true && this.categoryId !== null && !this.saving && !this.createdOffer
      && this.currencySchedules.every((schedule) => this.scheduleIsValid(schedule));
  }

  createDraft(form: NgForm): void {
    this.successMessage = '';
    this.errorMessage = '';
    if (!this.canSubmit(form)) {
      form.control.markAllAsTouched();
      this.errorMessage = 'Complete both currency schedules, amount limits, and all five rate bands before saving.';
      return;
    }

    const request: FdOfferCreateRequest = {
      productCode: this.productCode.trim().toUpperCase(),
      productName: this.productName.trim(),
      categoryId: this.categoryId!,
      description: this.description.trim(),
      currencies: this.currencySchedules.map((schedule) => ({
        currencyCode: schedule.currencyCode,
        minimumAmount: this.parseAmount(schedule.minimumAmount)!,
        maximumAmount: this.parseAmount(schedule.maximumAmount)!,
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

  resetForm(form: NgForm): void {
    form.resetForm();
    this.productCode = '';
    this.productName = '';
    this.description = '';
    this.createdOffer = null;
    this.categoryId = this.categories.find((category) => category.categoryCode === 'FIXED_DEPOSIT')?.categoryId
      ?? this.categories[0]?.categoryId ?? null;
    this.currencySchedules = [this.newSchedule('USD'), this.newSchedule('INR')];
    this.selectedCurrencyCode = 'USD';
    this.successMessage = '';
    this.errorMessage = '';
  }

  scheduleIsValid(schedule: CurrencyScheduleDraft): boolean {
    const minimum = this.parseAmount(schedule.minimumAmount);
    const maximum = this.parseAmount(schedule.maximumAmount);
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

  private parseAmount(value: string): number | null {
    const normalized = value.replace(/[^\d.-]/g, '');
    if (!normalized || !/^\d+(\.\d{0,2})?$/.test(normalized)) return null;
    const amount = Number(normalized);
    return Number.isFinite(amount) ? amount : null;
  }

  private newSchedule(currencyCode: 'USD' | 'INR'): CurrencyScheduleDraft {
    return {
      currencyCode,
      minimumAmount: '',
      maximumAmount: '',
      rateBands: this.bandDurations.map((durationMonths) => ({ durationMonths, annualRatePercent: null })),
    };
  }

  private errorText(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) return 'Your session expired. Sign in again with a staff account.';
    if (error.status === 403) return 'Product setup requires the BANK_OFFICER or ADMIN role.';
    const message = error.error?.message ?? error.error?.error;
    return typeof message === 'string' && message ? message : fallback;
  }
}
