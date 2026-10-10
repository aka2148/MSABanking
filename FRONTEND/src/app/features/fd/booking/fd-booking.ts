import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AccountResponse,
  ApiErrorResponse,
  BookingRequest,
  CurrencyOffer,
  ProductOffer,
  RateTier,
} from '../../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../../core/services/fd.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';
import { GlassSelect, GlassSelectOption } from '../../../shared/components/glass-select/glass-select';

@Component({
  selector: 'app-fd-booking',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, GlassShell, GlassSelect],
  templateUrl: './fd-booking.html',
  styleUrl: './fd-booking.scss',
})
export class FdBooking implements OnInit {
  step = 1;
  loading = true;
  submitting = false;
  errorMessage = '';
  bookingError = '';
  selectedProduct: ProductOffer | null = null;
  selectedCurrencyOffer: CurrencyOffer | null = null;
  selectedRateTier: RateTier | null = null;
  submittedAccount: AccountResponse | null = null;
  tenureOptions: GlassSelectOption[] = [];
  readonly bookingForm: FormGroup<{
    principalAmount: FormControl<string>;
    tenureMonths: FormControl<string>;
  }>;

  constructor(
    private readonly fdService: FdService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly fb: FormBuilder
  ) {
    this.bookingForm = this.fb.nonNullable.group({
      principalAmount: ['', [this.requiredValue()]],
      tenureMonths: ['', [this.requiredValue()]],
    });
  }

  ngOnInit(): void {
    const productId = Number(this.route.snapshot.paramMap.get('productId'));
    if (!Number.isInteger(productId) || productId <= 0) {
      this.loading = false;
      this.errorMessage = 'The selected FD product could not be identified.';
      return;
    }

    this.fdService.listProducts().subscribe({
      next: (products) => {
        const product = products.find((offer) => offer.product_id === productId) ?? null;
        this.loading = false;
        if (!product) {
          this.errorMessage = 'This FD offer is no longer eligible. Return to products and choose another offer.';
          return;
        }
        const currencyCode = this.route.snapshot.queryParamMap.get('currency')?.toUpperCase();
        const minimumMonths = Number(this.route.snapshot.queryParamMap.get('minMonths'));
        const maximumMonths = Number(this.route.snapshot.queryParamMap.get('maxMonths'));
        const currency = product.currency_offers.find((offer) => offer.currency_code === currencyCode);
        const tier = currency?.rate_tiers.find((rate) => rate.minimum_tenure_months === minimumMonths
          && rate.maximum_tenure_months === maximumMonths);
        if (!currency || !tier) {
          this.errorMessage = 'Choose a valid currency and rate option from the FD offers page.';
          return;
        }
        this.selectedProduct = product;
        this.configureSelectedOption(currency, tier);
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'We could not load this FD offer. Please return to products and try again.';
      },
    });
  }

  nextStep(): void {
    if (this.step === 1) {
      this.step = 2;
      return;
    }

    if (this.step === 2) {
      if (this.bookingForm.invalid) {
        this.bookingForm.markAllAsTouched();
        this.bookingError = 'Check the amount and exact term before reviewing the booking.';
        return;
      }
      this.bookingError = '';
      this.step = 3;
    }
  }

  previousStep(): void {
    if (this.submitting) return;
    this.bookingError = '';
    this.step = Math.max(1, this.step - 1);
  }

  submitBooking(): void {
    if (this.submitting || this.submittedAccount || !this.selectedProduct
        || !this.selectedCurrencyOffer || !this.selectedRateTier) return;
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      this.step = 2;
      this.bookingError = 'Check the booking details before confirming.';
      return;
    }

    const values = this.bookingForm.getRawValue();
    const request: BookingRequest = {
      product_id: this.selectedProduct.product_id,
      principal_amount: Number(values.principalAmount),
      tenure_months: Number(values.tenureMonths),
      currency_code: this.selectedCurrencyOffer.currency_code,
    };

    this.fdService.startSubmission();
    this.submitting = true;
    this.bookingError = '';
    this.fdService.book(request).subscribe({
      next: (account) => {
        this.submitting = false;
        if (!Number.isInteger(account.account_id) || account.account_id <= 0) {
          this.bookingError = 'The booking response did not include an account id. No booking success is shown.';
          return;
        }
        this.submittedAccount = account;
        this.fdService.completeSubmission();
        void this.router.navigate(['/fd/accounts', account.account_id]);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting = false;
        const apiError = normalizeApiError(error);
        this.bookingError = this.messageFor(apiError);
      },
    });
  }

  fieldError(controlName: 'principalAmount' | 'tenureMonths'): string {
    const control = this.bookingForm.controls[controlName];
    if (!control.touched || !control.errors) return '';
    if (control.errors['requiredValue']) return 'This field is required.';
    if (control.errors['amountBounds']) return 'Enter an amount within the offer range and at least 0.01.';
    if (control.errors['tenureBounds']) return 'Choose a tenure within the offer range.';
    return 'Check this value.';
  }

  private configureSelectedOption(currency: CurrencyOffer, tier: RateTier): void {
    this.selectedCurrencyOffer = currency;
    this.selectedRateTier = tier;
    this.tenureOptions = [];
    for (let month = tier.minimum_tenure_months; month <= tier.maximum_tenure_months; month += 1) {
      this.tenureOptions.push({ label: `${month} months`, value: String(month) });
    }
    this.bookingForm.controls.principalAmount.setValidators([
      this.requiredValue(), this.amountBounds(currency.minimum_amount, currency.maximum_amount),
    ]);
    this.bookingForm.controls.tenureMonths.setValidators([
      this.requiredValue(), this.tenureBounds(tier.minimum_tenure_months, tier.maximum_tenure_months),
    ]);
    this.bookingForm.updateValueAndValidity();
  }

  private messageFor(error: ApiErrorResponse): string {
    if (error.status === 400) {
      const fields = Object.entries(error.validationFields).map(([field, message]) => `${field}: ${message}`);
      return fields.length > 0 ? fields.join(' ') : error.message;
    }
    if (error.status === 401) return 'Your session has expired. Please sign in again.';
    if (error.status === 404) return 'This FD product is no longer available. Return to products and choose another offer.';
    if (error.status === 409) return `${error.message} ${error.replayHint ?? 'Review the current booking result before retrying.'}`;
    if (error.status === 422) return 'This booking is not supported for the selected product. Return to products and choose another offer.';
    return 'The FD booking service is temporarily unavailable. Please retry without changing the details.';
  }

  private requiredValue(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null =>
      String(control.value ?? '').trim() ? null : { requiredValue: true };
  }

  private amountBounds(minimum: number, maximum: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = Number(control.value);
      return Number.isFinite(value) && value >= 0.01 && value >= minimum && value <= maximum
        ? null
        : { amountBounds: true };
    };
  }

  private tenureBounds(minimum: number, maximum: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = Number(control.value);
      return Number.isInteger(value) && value >= minimum && value <= maximum ? null : { tenureBounds: true };
    };
  }

}
