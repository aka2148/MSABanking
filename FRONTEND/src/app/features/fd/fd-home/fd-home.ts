import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrencyOffer, ProductOffer, RateTier } from '../../../core/models/fd.models';
import { FdService } from '../../../core/services/fd.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

@Component({
  selector: 'app-fd-home',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, WorkspaceShell],
  templateUrl: './fd-home.html',
  styleUrl: './fd-home.scss',
})
export class FdHome implements OnInit {
  products: ProductOffer[] = [];
  loading = true;
  errorMessage = '';
  selectingOptionKey: string | null = null;

  constructor(
    private readonly fdService: FdService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.loading = true;
    this.errorMessage = '';
    this.fdService.listProducts().subscribe({
      next: (products) => {
        this.products = products;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'We could not load eligible FD products. Please try again.';
      },
    });
  }

  optionKey(product: ProductOffer, currency: CurrencyOffer, tier: RateTier): string {
    return `${product.product_id}:${currency.currency_code}:${tier.minimum_tenure_months}:${tier.maximum_tenure_months}`;
  }

  formatFrequency(value: string | null | undefined): string {
    if (!value) return 'not specified';
    return value.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
  }

  chooseRateTier(product: ProductOffer, currency: CurrencyOffer, tier: RateTier): void {
    if (this.loading || this.selectingOptionKey !== null) return;
    const key = this.optionKey(product, currency, tier);
    this.selectingOptionKey = key;
    this.router.navigate(['/fd/book', product.product_id], {
      queryParams: {
        currency: currency.currency_code,
        minMonths: tier.minimum_tenure_months,
        maxMonths: tier.maximum_tenure_months,
      },
    }).finally(() => {
      this.selectingOptionKey = null;
    });
  }
}
