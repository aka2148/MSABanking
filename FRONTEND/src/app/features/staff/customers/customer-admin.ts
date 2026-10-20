import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CustomerAdminPage, CustomerAdminRecord, CustomerAdminService } from '../../../core/services/customer-admin.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

@Component({
  selector: 'app-customer-admin',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  templateUrl: './customer-admin.html',
  styleUrl: './customer-admin.scss',
})
export class CustomerAdmin implements OnInit {
  page: CustomerAdminPage | null = null;
  search = '';
  loading = false;
  savingId: number | null = null;
  error = '';
  message = '';
  readonly kycStatuses = ['PENDING', 'VERIFIED', 'REJECTED'];
  readonly riskCategories = ['LOW', 'MEDIUM', 'HIGH'];

  constructor(private readonly customers: CustomerAdminService) {}

  ngOnInit(): void { this.load(0); }

  load(page: number): void {
    this.loading = true;
    this.error = '';
    this.customers.list(page, this.search).subscribe({
      next: (result) => { this.page = result; this.loading = false; },
      error: (error: HttpErrorResponse) => { this.error = this.errorText(error); this.loading = false; },
    });
  }

  save(customer: CustomerAdminRecord): void {
    this.savingId = customer.id;
    this.error = '';
    this.message = '';
    this.customers.update(customer.id, {
      fullName: customer.fullName.trim(),
      kycStatus: customer.kycStatus,
      riskCategory: customer.riskCategory,
      legalEntityType: customer.legalEntityType,
    }).subscribe({
      next: (updated) => {
        if (this.page) this.page.content = this.page.content.map((row) => row.id === updated.id ? updated : row);
        this.message = `Customer ${updated.customerNumber} updated.`;
        this.savingId = null;
      },
      error: (error: HttpErrorResponse) => { this.error = this.errorText(error); this.savingId = null; },
    });
  }

  private errorText(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Your session expired. Sign in again.';
    if (error.status === 403) return 'Customer management requires a staff account.';
    return error.error?.message ?? error.error?.error ?? 'Could not load or update customers.';
  }
}
