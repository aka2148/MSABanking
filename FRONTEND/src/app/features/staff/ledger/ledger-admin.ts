import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, Observable, expand, forkJoin, reduce } from 'rxjs';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { AdminLedgerEntry, AdminLedgerPage, AdminLedgerService } from '../../../core/services/admin-ledger.service';
import { StaffFdRow, StaffOperationsService, StaffPage, StaffSavingsRow } from '../../../core/services/staff-operations.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

type LedgerScope = 'BRANCH' | 'ACCOUNT' | 'FIXED_DEPOSIT';

@Component({
  selector: 'app-ledger-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, WorkspaceShell],
  templateUrl: './ledger-admin.html',
  styleUrl: './ledger-admin.scss',
})
export class LedgerAdmin implements OnInit {
  scope: LedgerScope = 'BRANCH';
  selectedAccountId: number | null = null;
  selectedFixedDepositId: number | null = null;
  savingsAccounts: StaffSavingsRow[] = [];
  fixedDeposits: StaffFdRow[] = [];
  filtersLoading = false;
  filtersError = '';
  ledger: AdminLedgerPage | null = null;
  pageNumber = 0;
  loading = false;
  error = '';

  constructor(private readonly ledgerApi: AdminLedgerService, private readonly staffApi: StaffOperationsService) {}

  ngOnInit(): void {
    this.loadFilters();
    this.load(0);
  }

  onScopeChange(): void {
    this.selectedAccountId = null;
    this.selectedFixedDepositId = null;
    this.clearResults();
  }

  onFilterSelectionChange(): void {
    this.clearResults();
  }

  applyFilter(): void {
    if (this.scope === 'ACCOUNT' && this.selectedAccountId === null) {
      this.ledger = null;
      this.error = 'Choose a savings account to filter the ledger.';
      return;
    }
    if (this.scope === 'FIXED_DEPOSIT' && this.selectedFixedDepositId === null) {
      this.ledger = null;
      this.error = 'Choose a fixed deposit to filter the ledger.';
      return;
    }
    this.load(0);
  }

  resetFilter(): void {
    this.scope = 'BRANCH';
    this.selectedAccountId = null;
    this.selectedFixedDepositId = null;
    this.load(0);
  }

  load(page: number): void {
    this.loading = true;
    this.error = '';
    this.ledger = null;
    this.pageNumber = page;
    const accountId = this.scope === 'ACCOUNT' ? this.selectedAccountId : null;
    const fixedDepositId = this.scope === 'FIXED_DEPOSIT' ? this.selectedFixedDepositId : null;
    this.ledgerApi.entries(page, accountId, fixedDepositId).subscribe({
      next: result => { this.ledger = result; this.loading = false; },
      error: (response: HttpErrorResponse) => { this.error = this.errorText(response); this.loading = false; },
    });
  }

  operationLabel(operation: string): string {
    const labels: Record<string, string> = {
      FUNDING: 'Deposit funding',
      INTEREST: 'Interest recognition',
      SETTLEMENT: 'Maturity settlement',
      DEMO_OPENING: 'Demo account opening',
    };
    return labels[operation] ?? 'Account entry';
  }

  accountLabel(accountCode: string): string {
    const labels: Record<string, string> = {
      SAVINGS: 'Savings account',
      FIXED_DEPOSIT: 'Fixed deposit',
      INTEREST_EXPENSE: 'Interest expense',
      DEMO_CASH: 'Demonstration funds',
    };
    return labels[accountCode] ?? accountCode;
  }

  get pageCount(): number {
    return this.ledger ? Math.max(1, Math.ceil(this.ledger.totalElements / this.ledger.size)) : 1;
  }

  private loadFilters(): void {
    this.filtersLoading = true;
    this.filtersError = '';
    forkJoin({
      savings: this.collectPages(page => this.staffApi.savings(page, '', 100)),
      fixedDeposits: this.collectPages(page => this.staffApi.fixedDeposits(page, '', 100)),
    }).subscribe({
      next: options => {
        this.savingsAccounts = options.savings;
        this.fixedDeposits = options.fixedDeposits;
        this.filtersLoading = false;
      },
      error: (response: HttpErrorResponse) => {
        this.filtersError = this.errorText(response);
        this.filtersLoading = false;
      },
    });
  }

  private clearResults(): void {
    this.ledger = null;
    this.error = '';
  }

  private collectPages<T>(fetch: (page: number) => Observable<StaffPage<T>>): Observable<T[]> {
    return fetch(0).pipe(
      expand(page => (page.number + 1) * page.size < page.totalElements ? fetch(page.number + 1) : EMPTY),
      reduce((all, page) => all.concat(page.content), [] as T[]),
    );
  }

  private errorText(response: HttpErrorResponse): string {
    if (response.status === 401) return 'Your session expired. Sign in again.';
    if (response.status === 403) return 'The ledger is available to administrators only.';
    return response.error?.message ?? 'Could not load ledger entries.';
  }
}
