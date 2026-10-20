import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import {
  FdOfferAdmin,
  FdProductAdminService,
} from '../../../core/services/fd-product-admin.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

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
  offers: FdOfferAdmin[] = [];
  loading = true;
  activatingId: number | null = null;
  editingTranslationId: number | null = null;
  savingTranslationId: number | null = null;
  translationDrafts: Record<number, ProductTranslationDraft> = {};
  editingProductId: number | null = null;
  savingProductId: number | null = null;
  productEditDrafts: Record<number, ProductEditDraft> = {};
  errorMessage = '';
  successMessage = '';
  offerPage = 0;
  readonly offerPageSize = 8;

  constructor(private readonly service: FdProductAdminService, private readonly auth: AuthService) {}

  get sessionRoleLabel(): string {
    return (this.auth.currentRole() ?? 'UNKNOWN').replace(/^ROLE_/, '').replaceAll('_', ' ');
  }

  get listedOffers(): FdOfferAdmin[] {
    return this.offers;
  }

  get visibleOffers(): FdOfferAdmin[] {
    const start = this.offerPage * this.offerPageSize;
    return this.listedOffers.slice(start, start + this.offerPageSize);
  }

  get offerPageCount(): number { return Math.max(1, Math.ceil(this.listedOffers.length / this.offerPageSize)); }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.service.listOffers().subscribe({
      next: (offers) => {
        this.offers = offers;
        this.offerPage = 0;
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.errorText(error, 'Could not load fixed-deposit offers.'); this.loading = false; },
    });
  }

  activate(offer: FdOfferAdmin): void {
    this.activatingId = offer.fd_product_id;
    this.errorMessage = '';
    this.successMessage = '';
    this.service.activateOffer(offer.fd_product_id).subscribe({
      next: (active) => {
        this.offers = this.offers.map((existing) => existing.fd_product_id === active.fd_product_id ? active : existing);
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

  private errorText(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) return 'Your session expired. Sign in again with a staff account.';
    if (error.status === 403) {
      const endpoint = error.url?.replace(/^https?:\/\/[^/]+/, '').split('?')[0] || 'the product service';
      return `The product service denied ${endpoint} for this session (${this.auth.currentRole() ?? 'unknown role'}). Sign out and back in after confirming your account has BANK_OFFICER or ADMIN access.`;
    }
    const message = error.error?.message ?? error.error?.error;
    return typeof message === 'string' && message ? message : fallback;
  }
}
