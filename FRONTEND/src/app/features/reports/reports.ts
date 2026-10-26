import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { RouterLink } from '@angular/router';
import {
  FdSummaryRow, InterestHistoryRow, MaturitySummaryRow, NoticeRecord, PortfolioReport,
  ReportsService, StatementRecord,
} from '../../core/services/reports.service';
import { AuthService } from '../../core/services/auth.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';

type ReportKey = 'portfolio' | 'fd-summary' | 'maturity-summary' | 'interest-history' | 'statements' | 'notices';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports implements OnInit {
  readonly staff: boolean;
  readonly reports: { key: ReportKey; label: string }[];
  active: ReportKey = 'portfolio';
  username = '';
  loading = false;
  error = '';
  portfolio: PortfolioReport | null = null;
  fdSummary: FdSummaryRow[] = [];
  maturitySummary: MaturitySummaryRow[] = [];
  interestHistory: InterestHistoryRow[] = [];
  statements: StatementRecord[] = [];
  notices: NoticeRecord[] = [];

  constructor(private readonly service: ReportsService, auth: AuthService) {
    this.staff = ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(auth.currentRole() ?? '');
    if (this.staff) this.active = 'fd-summary';
    this.reports = this.staff
      ? [
          { key: 'fd-summary', label: 'FD status summary' },
          { key: 'maturity-summary', label: 'Maturity summary' },
          { key: 'portfolio', label: 'Customer portfolio' },
          { key: 'interest-history', label: 'Interest history' },
          { key: 'statements', label: 'Statements' },
          { key: 'notices', label: 'Notices' },
        ]
      : [
          { key: 'portfolio', label: 'My FD portfolio' },
          { key: 'interest-history', label: 'Interest history' },
          { key: 'statements', label: 'Statements' },
          { key: 'notices', label: 'Notices' },
        ];
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const requestedUsername = this.staff ? this.username.trim() || undefined : undefined;
    const request: Observable<unknown> = this.active === 'fd-summary'
      ? this.service.fdSummary()
      : this.active === 'maturity-summary'
        ? this.service.maturitySummary()
        : this.active === 'portfolio'
          ? this.service.portfolio(requestedUsername)
          : this.active === 'interest-history'
            ? this.service.interestHistory(requestedUsername)
            : this.active === 'statements'
              ? this.service.statements(requestedUsername)
              : this.service.notices(requestedUsername);
    request.subscribe({
      next: (data) => {
        switch (this.active) {
          case 'fd-summary': this.fdSummary = data as FdSummaryRow[]; break;
          case 'maturity-summary': this.maturitySummary = data as MaturitySummaryRow[]; break;
          case 'portfolio': this.portfolio = data as PortfolioReport; break;
          case 'interest-history': this.interestHistory = data as InterestHistoryRow[]; break;
          case 'statements': this.statements = data as StatementRecord[]; break;
          case 'notices': this.notices = data as NoticeRecord[]; break;
        }
        this.loading = false;
      },
      error: (response: HttpErrorResponse) => {
        this.error = response.status === 403
          ? 'Your account cannot access this report.'
          : response.status === 404
            ? 'No customer report was found for that username.'
            : response.error?.message ?? 'Could not load this report. Try again.';
        this.loading = false;
      },
    });
  }

  select(report: ReportKey): void {
    this.active = report;
    this.load();
  }

  loadCustomer(): void {
    this.active = 'portfolio';
    this.load();
  }

  download(): void {
    const requestedUsername = this.staff ? this.username.trim() || undefined : undefined;
    this.service.downloadCsv(this.active, requestedUsername).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${this.active}.csv`;
        anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: (response: HttpErrorResponse) => {
        this.error = response.status === 403 ? 'Your account cannot download this report.' : 'Could not download this CSV. Try again.';
      },
    });
  }

  formatMoney(value: number, scale: number): string {
    return new Intl.NumberFormat(undefined, { minimumFractionDigits: scale, maximumFractionDigits: scale }).format(value);
  }

  portfolioScale(currency: string): number {
    const activeCurrency = this.portfolio?.currencies.find((item) => item.currencyCode === currency);
    if (activeCurrency) return activeCurrency.decimalPlaces;
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
    } catch {
      return 2;
    }
  }
}
