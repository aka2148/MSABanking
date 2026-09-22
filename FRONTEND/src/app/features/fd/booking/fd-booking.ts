import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AccountResponse,
  ApiErrorResponse,
  BookingRequest,
  ProductOffer,
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
  submittedAccount: AccountResponse | null = null;
  tenureOptions: GlassSelectOption[] = [];
  readonly currencyOptions: GlassSelectOption[] = [];
  readonly bookingForm = this.fb.nonNullable.group({
    principalAmount: ['', [this.requiredValue()]],
    tenureMonths: ['', [this.requiredValue()]],
    currencyCode: ['', [this.requiredValue()]],
  });

  constructor(
    private readonly fdService: FdService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly fb: FormBuilder
  ) {}

  ngOnInit(): void {
    const productId = Number(this.route.snapshot.paramMap.get('productId'));
    if (!Number.isInteger(productId) || productId <= 0) {
      this.loading = false;
      this.errorMessage = 'The selected FD product could not be identified.';
      return;
    }

    this.fdService.listProducts().subscribe({
      next: (products) => {
        this.selectedProduct = products.find((product) => product.product_id === productId) ?? null;
        this.loading = false;
        if (!this.selectedProduct) {
          this.errorMessage = 'This FD offer is no longer eligible. Return to products and choose another offer.';
          return;
        }
        this.configureProductFields(this.selectedProduct);
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
        this.bookingError = 'Check the amount, tenure, and currency before reviewing the booking.';
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
    if (this.submitting || this.submittedAccount || !this.selectedProduct) return;
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
      currency_code: values.currencyCode,
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

  fieldError(controlName: 'principalAmount' | 'tenureMonths' | 'currencyCode'): string {
    const control = this.bookingForm.controls[controlName];
    if (!control.touched || !control.errors) return '';
    if (control.errors['requiredValue']) return 'This field is required.';
    if (control.errors['amountBounds']) return 'Enter an amount within the offer range and at least 0.01.';
    if (control.errors['tenureBounds']) return 'Choose a tenure within the offer range.';
    if (control.errors['currencyMismatch']) return 'Use the currency provided by the server offer.';
    return 'Check this value.';
  }

  private configureProductFields(product: ProductOffer): void {
    this.tenureOptions = [];
    for (let month = product.minimum_tenure_months; month <= product.maximum_tenure_months; month += 1) {
      this.tenureOptions.push({ label: `${month} months`, value: String(month) });
    }
    this.currencyOptions.splice(0, this.currencyOptions.length, {
      label: product.currency_code,
      value: product.currency_code,
    });
    this.bookingForm.controls.principalAmount.setValidators([
      this.requiredValue(), this.amountBounds(product.minimum_amount, product.maximum_amount),
    ]);
    this.bookingForm.controls.tenureMonths.setValidators([
      this.requiredValue(), this.tenureBounds(product.minimum_tenure_months, product.maximum_tenure_months),
    ]);
    this.bookingForm.controls.currencyCode.setValidators([
      this.requiredValue(), this.currencyMatch(product.currency_code),
    ]);
    this.bookingForm.patchValue({ currencyCode: product.currency_code });
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

  private currencyMatch(currency: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null =>
      control.value === currency ? null : { currencyMismatch: true };
  }
}
