import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FdAccountSummary } from '../../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../../core/services/fd.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

const PAGE_SIZE = 20;
const BOOKING_PATH = '/accounts';

@Component({
  selector: 'app-my-deposits',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, WorkspaceShell],
  templateUrl: './my-deposits.html',
  styleUrl: './my-deposits.scss',
})
export class MyDeposits implements OnInit {
  accounts: FdAccountSummary[] = [];
  page = 1;
  totalRecords = 0;
  loading = true;
  loaded = false;
  errorMessage = '';
  pendingBookingReview = false;

  constructor(private readonly fdService: FdService) {}

  ngOnInit(): void {
    this.pendingBookingReview = this.fdService.hasPendingSubmission(BOOKING_PATH);
    this.load();
  }

  load(page = this.page): void {
    this.page = page;
    this.loading = true;
    this.errorMessage = '';
    this.fdService.listAccounts(page, PAGE_SIZE).subscribe({
      next: (response) => {
        this.accounts = response.data;
        this.totalRecords = response.pagination.total_records;
        this.page = response.pagination.page;
        this.loaded = true;
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = normalizeApiError(error).message;
      },
    });
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalRecords / PAGE_SIZE));
  }

  formatRate(rate: number | null): string {
    return rate === null ? '—' : `${(rate * 100).toFixed(2)}%`;
  }

  clearPendingBookingAfterReview(): void {
    this.fdService.clearPendingSubmission(BOOKING_PATH);
    this.pendingBookingReview = false;
  }
}
