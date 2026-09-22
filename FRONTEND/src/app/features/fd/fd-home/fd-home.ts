import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ProductOffer } from '../../../core/models/fd.models';
import { FdService } from '../../../core/services/fd.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-fd-home',
  standalone: true,
  imports: [CommonModule, RouterLink, GlassShell],
  templateUrl: './fd-home.html',
  styleUrl: './fd-home.scss',
})
export class FdHome implements OnInit {
  products: ProductOffer[] = [];
  loading = true;
  errorMessage = '';
  selectingProductId: number | null = null;

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

  browseProduct(product: ProductOffer): void {
    if (this.loading || this.selectingProductId !== null) return;
    this.selectingProductId = product.product_id;
    this.router.navigate(['/fd/book', product.product_id]).finally(() => {
      this.selectingProductId = null;
    });
  }
}
