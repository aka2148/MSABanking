import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, forkJoin, of, switchMap, takeUntil } from 'rxjs';
import { CurrencyOffer, ProductOffer, RateTier } from '../../../core/models/fd.models';
import { FdEstimate, FdService, normalizeApiError } from '../../../core/services/fd.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

export interface ProductComparisonItem {
  product: ProductOffer;
  currency: CurrencyOffer;
  isEligible: boolean;
  ineligibilityReason?: string;
  estimate: FdEstimate | null;
  loading: boolean;
  error?: string;
  isBestReturn?: boolean;
}

@Component({
  selector: 'app-fd-calculator',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  templateUrl: './fd-calculator.html',
  styleUrl: './fd-calculator.scss',
})
export class FdCalculator implements OnInit, OnDestroy {
  private readonly destroyed = new Subject<void>();
  private readonly inputChange$ = new Subject<void>();

  // Products
  loadingProducts = true;
  productsError = '';
  products: ProductOffer[] = [];
  selectedProduct: ProductOffer | null = null;
  selectedCurrencyOffer: CurrencyOffer | null = null;

  // Active Tab: 'calculator' | 'compare' | 'catalog'
  activeView: 'calculator' | 'compare' | 'catalog' = 'calculator';

  // Calculator Inputs
  principalAmount = 100000;
  tenureMonths = 12;
  selectedCurrencyCode = 'INR';

  // Quick Preset Chips
  amountPresets = [25000, 50000, 100000, 500000, 1000000];
  tenurePresets = [
    { label: '6m', months: 6 },
    { label: '1 yr', months: 12 },
    { label: '2 yrs', months: 24 },
    { label: '3 yrs', months: 36 },
    { label: '5 yrs', months: 60 },
  ];

  // Calculation State
  estimate: FdEstimate | null = null;
  estimateLoading = false;
  estimateError = '';
  validationError = '';

  // Comparison State
  comparisonItems: ProductComparisonItem[] = [];
  loadingComparison = false;

  constructor(
    private readonly fdService: FdService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // Setup debounced recalculation
    this.inputChange$
      .pipe(
        debounceTime(250),
        takeUntil(this.destroyed)
      )
      .subscribe(() => {
        this.runCalculation();
        if (this.activeView === 'compare') {
          this.runComparison();
        }
      });

    this.loadProducts();
  }

  ngOnDestroy(): void {
    this.destroyed.next();
    this.destroyed.complete();
  }

  loadProducts(): void {
    this.loadingProducts = true;
    this.productsError = '';
    this.fdService.listProducts()
      .pipe(takeUntil(this.destroyed))
      .subscribe({
        next: (offers) => {
          this.loadingProducts = false;
          this.products = offers;
          if (this.products.length === 0) {
            this.productsError = 'No eligible fixed-deposit products are currently available.';
            return;
          }

          // Check query parameters to pre-select product or values
          const queryProductId = Number(this.route.snapshot.queryParamMap.get('product'));
          const queryCurrency = this.route.snapshot.queryParamMap.get('currency')?.toUpperCase();
          const queryAmount = Number(this.route.snapshot.queryParamMap.get('amount'));
          const queryMonths = Number(this.route.snapshot.queryParamMap.get('months'));

          let initialProduct = this.products.find(p => p.product_id === queryProductId);
          if (!initialProduct) {
            initialProduct = this.products[0];
          }

          this.selectProduct(initialProduct);

          if (queryCurrency && this.selectedProduct?.currency_offers.some(c => c.currency_code === queryCurrency)) {
            this.selectedCurrencyCode = queryCurrency;
            this.updateSelectedCurrencyOffer();
          }

          if (queryAmount > 0) {
            this.principalAmount = queryAmount;
          }
          if (queryMonths > 0) {
            this.tenureMonths = queryMonths;
          }

          this.validateAndCalculate();
        },
        error: (err) => {
          this.loadingProducts = false;
          this.productsError = normalizeApiError(err).message;
        }
      });
  }

  selectProduct(product: ProductOffer): void {
    this.selectedProduct = product;
    // Default to INR if available, else first currency offer
    const inrOffer = product.currency_offers.find(c => c.currency_code === 'INR');
    this.selectedCurrencyOffer = inrOffer || product.currency_offers[0] || null;
    this.selectedCurrencyCode = this.selectedCurrencyOffer?.currency_code || 'INR';

    // Ensure tenure is within bounds
    if (this.selectedCurrencyOffer) {
      if (this.tenureMonths < this.selectedCurrencyOffer.minimum_tenure_months) {
        this.tenureMonths = this.selectedCurrencyOffer.minimum_tenure_months;
      } else if (this.tenureMonths > this.selectedCurrencyOffer.maximum_tenure_months) {
        this.tenureMonths = this.selectedCurrencyOffer.maximum_tenure_months;
      }
      // Ensure principal is within bounds
      if (this.principalAmount < this.selectedCurrencyOffer.minimum_amount) {
        this.principalAmount = this.selectedCurrencyOffer.minimum_amount;
      } else if (this.principalAmount > this.selectedCurrencyOffer.maximum_amount) {
        this.principalAmount = this.selectedCurrencyOffer.maximum_amount;
      }
    }

    this.onInputChange();
  }

  onCurrencyChange(): void {
    this.updateSelectedCurrencyOffer();
    this.onInputChange();
  }

  private updateSelectedCurrencyOffer(): void {
    if (!this.selectedProduct) return;
    this.selectedCurrencyOffer = this.selectedProduct.currency_offers.find(
      c => c.currency_code === this.selectedCurrencyCode
    ) || null;
  }

  onInputChange(): void {
    this.inputChange$.next();
  }

  setAmountPreset(amount: number): void {
    this.principalAmount = amount;
    this.onInputChange();
  }

  setTenurePreset(months: number): void {
    this.tenureMonths = months;
    this.onInputChange();
  }

  validateAndCalculate(): void {
    this.validationError = '';
    if (!this.selectedProduct || !this.selectedCurrencyOffer) {
      return;
    }

    const curr = this.selectedCurrencyOffer;
    if (!this.principalAmount || this.principalAmount <= 0) {
      this.validationError = 'Please enter a valid deposit amount.';
      this.estimate = null;
      return;
    }
    if (this.principalAmount < curr.minimum_amount) {
      this.validationError = `Minimum deposit for this product is ${this.formatCurrency(curr.minimum_amount, curr.currency_code)}.`;
      this.estimate = null;
      return;
    }
    if (this.principalAmount > curr.maximum_amount) {
      this.validationError = `Maximum deposit for this product is ${this.formatCurrency(curr.maximum_amount, curr.currency_code)}.`;
      this.estimate = null;
      return;
    }
    if (!this.tenureMonths || this.tenureMonths <= 0) {
      this.validationError = 'Please specify an investment tenure in months.';
      this.estimate = null;
      return;
    }
    if (this.tenureMonths < curr.minimum_tenure_months || this.tenureMonths > curr.maximum_tenure_months) {
      this.validationError = `Permitted tenure is ${curr.minimum_tenure_months} to ${curr.maximum_tenure_months} months for ${curr.currency_code}.`;
      this.estimate = null;
      return;
    }

    this.runCalculation();
  }

  private runCalculation(): void {
    if (!this.selectedProduct || !this.selectedCurrencyOffer) return;
    const curr = this.selectedCurrencyOffer;

    if (this.principalAmount < curr.minimum_amount || this.principalAmount > curr.maximum_amount ||
        this.tenureMonths < curr.minimum_tenure_months || this.tenureMonths > curr.maximum_tenure_months) {
      this.validateAndCalculate();
      return;
    }

    this.validationError = '';
    this.estimateLoading = true;
    this.estimateError = '';

    const req = {
      productId: this.selectedProduct.product_id,
      currencyCode: curr.currency_code,
      principal: this.principalAmount,
      tenureMonths: this.tenureMonths
    };

    this.fdService.estimate(req)
      .pipe(takeUntil(this.destroyed))
      .subscribe({
        next: (result) => {
          this.estimate = result;
          this.estimateLoading = false;
        },
        error: (err) => {
          this.estimateLoading = false;
          this.estimate = null;
          this.estimateError = normalizeApiError(err).message;
        }
      });
  }

  switchView(view: 'calculator' | 'compare' | 'catalog'): void {
    this.activeView = view;
    if (view === 'compare') {
      this.runComparison();
    }
  }

  runComparison(): void {
    if (this.products.length === 0) return;
    this.loadingComparison = true;

    const requests = this.products.map(product => {
      const currency = product.currency_offers.find(c => c.currency_code === this.selectedCurrencyCode) || product.currency_offers[0];
      if (!currency) {
        return of({
          product,
          currency: { currency_code: this.selectedCurrencyCode, decimal_places: 2, minimum_amount: 0, maximum_amount: 0, minimum_tenure_months: 0, maximum_tenure_months: 0, rate_tiers: [] },
          isEligible: false,
          ineligibilityReason: 'Currency not supported',
          estimate: null,
          loading: false
        } as ProductComparisonItem);
      }

      // Check bounds
      if (this.principalAmount < currency.minimum_amount || this.principalAmount > currency.maximum_amount) {
        return of({
          product,
          currency,
          isEligible: false,
          ineligibilityReason: `Amount outside limit (${this.formatCurrency(currency.minimum_amount, currency.currency_code)} – ${this.formatCurrency(currency.maximum_amount, currency.currency_code)})`,
          estimate: null,
          loading: false
        } as ProductComparisonItem);
      }

      if (this.tenureMonths < currency.minimum_tenure_months || this.tenureMonths > currency.maximum_tenure_months) {
        return of({
          product,
          currency,
          isEligible: false,
          ineligibilityReason: `Tenure outside range (${currency.minimum_tenure_months} – ${currency.maximum_tenure_months} months)`,
          estimate: null,
          loading: false
        } as ProductComparisonItem);
      }

      // Call estimate endpoint
      return this.fdService.estimate({
        productId: product.product_id,
        currencyCode: currency.currency_code,
        principal: this.principalAmount,
        tenureMonths: this.tenureMonths
      }).pipe(
        switchMap(est => of({
          product,
          currency,
          isEligible: true,
          estimate: est,
          loading: false
        } as ProductComparisonItem)),
        catchError(err => of({
          product,
          currency,
          isEligible: false,
          ineligibilityReason: normalizeApiError(err).message,
          estimate: null,
          loading: false,
          error: normalizeApiError(err).message
        } as ProductComparisonItem))
      );
    });

    forkJoin(requests)
      .pipe(takeUntil(this.destroyed))
      .subscribe(results => {
        this.loadingComparison = false;
        // Identify best return
        let maxMaturity = 0;
        results.forEach(res => {
          if (res.estimate && res.estimate.estimatedMaturityAmount > maxMaturity) {
            maxMaturity = res.estimate.estimatedMaturityAmount;
          }
        });

        this.comparisonItems = results.map(res => ({
          ...res,
          isBestReturn: res.estimate != null && res.estimate.estimatedMaturityAmount === maxMaturity && maxMaturity > 0
        }));
      });
  }

  bookProduct(product: ProductOffer, currencyCode: string): void {
    void this.router.navigate(['/fd/book', product.product_id], {
      queryParams: {
        currency: currencyCode,
        amount: this.principalAmount,
        months: this.tenureMonths
      }
    });
  }

  getRateRange(product: ProductOffer, currencyCode: string): string {
    const currency = product.currency_offers.find(c => c.currency_code === currencyCode);
    if (!currency || currency.rate_tiers.length === 0) return 'Rates on request';
    const rates = currency.rate_tiers.map(t => t.annual_rate * 100);
    const minRate = Math.min(...rates);
    const maxRate = Math.max(...rates);
    if (minRate === maxRate) {
      return `${minRate.toFixed(2)}% p.a.`;
    }
    return `${minRate.toFixed(2)}% – ${maxRate.toFixed(2)}% p.a.`;
  }

  formatCurrency(value: number | null | undefined, currencyCode = 'INR'): string {
    if (value === null || value === undefined || isNaN(value)) return '—';
    try {
      if (currencyCode === 'INR') {
        return new Intl.NumberFormat('en-IN', {
          style: 'currency',
          currency: 'INR',
          maximumFractionDigits: 0
        }).format(value);
      }
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currencyCode,
        maximumFractionDigits: 2
      }).format(value);
    } catch {
      return `${currencyCode} ${value.toLocaleString()}`;
    }
  }

  formatFrequency(value: string | null | undefined): string {
    if (!value) return 'not specified';
    return value.toLowerCase().split('_').map(p => p ? p[0].toUpperCase() + p.slice(1) : '').join(' ');
  }
}
