import { CustomerProductWarning } from '../../../shared/components/customer-product-warning';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
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
  SavingsAccount,
} from '../../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../../core/services/fd.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';
import { GlassSelect, GlassSelectOption } from '../../../shared/components/glass-select/glass-select';

@Component({
  selector: 'app-fd-booking',
  standalone: true,
  imports: [CustomerProductWarning, TranslatePipe, CommonModule, ReactiveFormsModule, RouterLink, WorkspaceShell, GlassSelect],
  templateUrl: './fd-booking.html',
  styleUrl: './fd-booking.scss',
})
export class FdBooking implements OnInit {
  productAccessBlocked = true;
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
  savingsOptions: GlassSelectOption[] = [];
  savingsAccounts: SavingsAccount[] = [];
  savingsLoading = false;
  readonly bookingForm: FormGroup<{
    fundingSavingsAccountId: FormControl<string>;
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
      fundingSavingsAccountId: ['', [this.requiredValue()]],
      principalAmount: ['', [this.requiredValue()]],
      tenureMonths: ['', [this.requiredValue()]],
    });
    this.bookingForm.controls.principalAmount.valueChanges.subscribe(() => this.updateSavingsOptions());
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
      if (!this.hasSufficientSavings()) { this.bookingError = 'The selected savings account does not have enough funds for this deposit.'; return; }
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
    if (this.productAccessBlocked) return;
    if (this.submitting || this.submittedAccount || !this.selectedProduct
        || !this.selectedCurrencyOffer || !this.selectedRateTier) return;
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      this.step = 2;
      this.bookingError = 'Check the booking details before confirming.';
      return;
    }
    if (!this.hasSufficientSavings()) { this.step = 2; this.bookingError = 'The selected savings account does not have enough funds for this deposit.'; return; }

    const values = this.bookingForm.getRawValue();
    const request: BookingRequest = {
      funding_savings_account_id: Number(values.fundingSavingsAccountId),
      product_id: this.selectedProduct.product_id,
      principal_amount: Number(values.principalAmount),
      tenure_months: Number(values.tenureMonths),
      currency_code: this.selectedCurrencyOffer.currency_code,
    };

    this.submitting = true;
    this.bookingError = '';
    this.fdService.book(request).subscribe({
      next: (account) => {
        this.submitting = false;
        if (!Number.isInteger(account.account_id) || account.account_id <= 0) {
          this.bookingError = 'We could not open the deposit details after booking. Check whether the deposit was created before trying again.';
          return;
        }
        this.submittedAccount = account;
        void this.router.navigate(['/fd/accounts', account.account_id]);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting = false;
        const apiError = normalizeApiError(error);
        this.bookingError = this.messageFor(apiError);
      },
    });
  }

  fieldError(controlName: 'principalAmount' | 'tenureMonths' | 'fundingSavingsAccountId'): string {
    const control = this.bookingForm.controls[controlName];
    if (!control.touched || !control.errors) return '';
    if (control.errors['requiredValue']) return 'This field is required.';
    if (control.errors['amountBounds']) return 'Enter an amount within the offer range and at least 0.01.';
    if (control.errors['tenureBounds']) return 'Choose a tenure within the offer range.';
    return 'Check this value.';
  }

  selectedSavingsAccountLabel(): string {
    const selected = this.bookingForm.controls.fundingSavingsAccountId.value;
    return this.savingsOptions.find((option) => option.value === selected)?.label ?? 'Selected savings account';
  }

  private hasSufficientSavings(): boolean {
    const values = this.bookingForm.getRawValue();
    const selected = this.savingsAccounts.find(account => account.account_id === Number(values.fundingSavingsAccountId));
    return !!selected && selected.status === 'ACTIVE' && selected.currency_code === this.selectedCurrencyOffer?.currency_code
      && selected.balance >= Number(values.principalAmount);
  }

  private maskAccount(number: string): string { return `•••• ${number.slice(-4)}`; }

  formatFrequency(value: string | null | undefined): string {
    if (!value) return 'not specified';
    return value.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
  }

  accountStatusLabel(status: AccountResponse['status']): string {
    const labels: Record<AccountResponse['status'], string> = {
      PENDING: 'Pending funding',
      ACTIVE: 'Active',
      MATURED: 'Matured',
      CLOSED: 'Closed',
      PREMATURELY_CLOSED: 'Closed early',
    };
    return labels[status];
  }

  private configureSelectedOption(currency: CurrencyOffer, tier: RateTier): void {
    this.savingsLoading = true;
    this.fdService.listSavings().subscribe({
      next: (accounts: SavingsAccount[]) => {
        this.savingsAccounts = accounts;
        this.updateSavingsOptions();
        this.savingsLoading = false;
      },
      error: (error: HttpErrorResponse) => { this.savingsLoading = false; this.bookingError = normalizeApiError(error).message; },
    });
    this.selectedCurrencyOffer = currency;
    this.selectedRateTier = tier;
    this.tenureOptions = Array.from(
      { length: tier.maximum_tenure_months - tier.minimum_tenure_months + 1 },
      (_, index) => {
        const months = tier.minimum_tenure_months + index;
        return { label: `${months} months`, value: String(months) };
      },
    );
    this.bookingForm.controls.principalAmount.setValidators([
      this.requiredValue(), this.amountBounds(currency.minimum_amount, currency.maximum_amount),
    ]);
    this.bookingForm.controls.tenureMonths.setValidators([
      this.requiredValue(), this.tenureBounds(tier.minimum_tenure_months, tier.maximum_tenure_months),
    ]);
    this.bookingForm.updateValueAndValidity();
  }

  private updateSavingsOptions(): void {
    const amount = Number(this.bookingForm.controls.principalAmount.value);
    this.savingsOptions = this.savingsAccounts.map(account => ({
      value: String(account.account_id),
      label: `${account.display_name} · ${this.maskAccount(account.account_number)} · ${account.currency_code} · ${account.balance.toFixed(2)} available · ${account.status}${amount > 0 && account.balance < amount ? ' · insufficient balance' : ''}`,
      disabled: account.currency_code !== this.selectedCurrencyOffer?.currency_code || account.status !== 'ACTIVE'
        || (amount > 0 && account.balance < amount),
    }));
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
