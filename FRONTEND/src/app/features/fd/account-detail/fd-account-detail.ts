import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AccountResponse,
  SavingsAccount,
  Journal,
  ApiErrorResponse,
  ClosureResponse,
  FdStatus,
  FundingResponse,
  HistoryEntry,
  PrematureClosureResponse,
} from '../../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../../core/services/fd.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

export type AccountAction = 'FUND' | 'CLOSE' | 'REQUEST_PREMATURE' | 'APPROVE_PREMATURE';
type Result =
  | { kind: 'funding'; value: FundingResponse }
  | { kind: 'closure'; value: ClosureResponse }
  | { kind: 'premature'; value: PrematureClosureResponse };

@Component({
  selector: 'app-fd-account-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, GlassShell],
  templateUrl: './fd-account-detail.html',
  styleUrl: './fd-account-detail.scss',
})
export class FdAccountDetail implements OnInit {
  private static readonly ACTIONS: Record<FdStatus, readonly AccountAction[]> = {
    PENDING: ['FUND'],
    ACTIVE: ['REQUEST_PREMATURE'],
    MATURED: ['CLOSE', 'REQUEST_PREMATURE'],
    CLOSED: [],
    PREMATURELY_CLOSED: [],
  };

  account: AccountResponse | null = null;
  savings: SavingsAccount | null = null;
  savingsError = '';
  journals: Journal[] = [];
  journalError = '';
  journalsLoading = false;
  get settlement(): Journal | null { return this.journals.find(j => j.operation === 'SETTLEMENT') ?? null; }
  history: HistoryEntry[] = [];
  prematureClosures: PrematureClosureResponse[] = [];
  selectedPrematureClosure: PrematureClosureResponse | null = null;
  result: Result | null = null;
  accountId = 0;
  loading = true;
  historyLoading = false;
  busyAction: AccountAction | null = null;
  pendingConfirmation: AccountAction | null = null;
  errorMessage = '';
  successMessage = '';

  constructor(
    private readonly fdService: FdService,
    private readonly route: ActivatedRoute,
  ) {}

  static actionsForStatus(status: FdStatus): readonly AccountAction[] {
    return FdAccountDetail.ACTIONS[status];
  }

  static requiresConfirmation(action: AccountAction): boolean {
    return action === 'FUND' || action === 'CLOSE' || action === 'REQUEST_PREMATURE' || action === 'APPROVE_PREMATURE';
  }

  ngOnInit(): void {
    this.accountId = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(this.accountId) || this.accountId <= 0) {
      this.loading = false;
      this.errorMessage = 'This deposit link is not valid. Return to Fixed Deposits and try again.';
      return;
    }
    this.loadAccount();
  }

  get actions(): readonly AccountAction[] {
    return this.account ? FdAccountDetail.actionsForStatus(this.account.status) : [];
  }

  get sortedHistory(): HistoryEntry[] {
    return [...this.history].sort((left, right) =>
      new Date(left.entry_date).getTime() - new Date(right.entry_date).getTime());
  }

  get canApprovePrematureClosure(): boolean {
    return this.prematureClosures.some((closure) => closure.status === 'PENDING');
  }

  get pendingClosure(): PrematureClosureResponse | null {
    return this.prematureClosures.find((closure) => closure.status === 'PENDING') ?? null;
  }

  get closureResult(): ClosureResponse | null {
    return this.result?.kind === 'closure' ? this.result.value : null;
  }

  get canSettleAtMaturity(): boolean {
    const account = this.account;
    if (!account || account.status !== 'MATURED' || this.journalsLoading || this.journalError
        || account.funding_savings_account_id == null) return false;

    const sourceId = account.funding_savings_account_id;
    const funding = this.journals.find(journal => journal.operation === 'FUNDING');
    if (!this.journalMatches(funding, sourceId, account.currency_code, account.principal_amount)) return false;

    const interest = Math.max(0, account.maturity_amount - account.principal_amount);
    if (interest === 0) return true;
    return this.journalMatches(
      this.journals.find(journal => journal.operation === 'INTEREST'),
      sourceId,
      account.currency_code,
      interest,
    );
  }

  get closureBlockMessage(): string {
    if (!this.account || this.account.status !== 'MATURED' || this.journalsLoading) return '';
    if (this.journalError) return 'Closure is unavailable because the settlement records could not be verified. Reload the account and try again.';
    if (this.account.funding_savings_account_id == null) {
      return 'This older deposit has no linked savings account on record. Its original funding must be reconciled before it can be closed and paid out.';
    }
    const sourceId = this.account.funding_savings_account_id;
    const funding = this.journals.find(journal => journal.operation === 'FUNDING');
    if (!this.journalMatches(funding, sourceId, this.account.currency_code, this.account.principal_amount)) {
      return 'This deposit has no verified savings funding record. Its historical funding must be reconciled before closure.';
    }
    const interest = Math.max(0, this.account.maturity_amount - this.account.principal_amount);
    if (interest > 0 && !this.journalMatches(
      this.journals.find(journal => journal.operation === 'INTEREST'),
      sourceId,
      this.account.currency_code,
      interest,
    )) {
      return 'The maturity interest has not been verified in the account records, so closure is unavailable.';
    }
    return '';
  }

  get prematureResult(): PrematureClosureResponse | null {
    return this.result?.kind === 'premature' ? this.result.value : null;
  }

  beginAction(action: AccountAction): void {
    if (!this.actions.includes(action) && action !== 'APPROVE_PREMATURE') return;
    if (action === 'FUND') this.fund();
    else if (action === 'CLOSE') this.close();
    else if (action === 'REQUEST_PREMATURE') this.requestPrematureClosure();
    else this.approvePrematureClosure();
  }

  cancelConfirmation(): void {
    if (!this.busyAction) this.pendingConfirmation = null;
  }

  confirmPendingAction(): void {
    const action = this.pendingConfirmation;
    if (!action || this.busyAction) return;
    this.pendingConfirmation = null;
    if (action === 'FUND') this.executeFunding();
    else if (action === 'CLOSE') this.executeClose();
    else if (action === 'REQUEST_PREMATURE') this.executePrematureRequest();
    else if (action === 'APPROVE_PREMATURE') this.executePrematureApproval();
  }

  fund(): void {
    if (!this.requireConfirmation('FUND')) return;
    this.executeFunding();
  }

  close(): void {
    if (!this.isAllowed('CLOSE') || !this.canSettleAtMaturity) return;
    if (!this.requireConfirmation('CLOSE')) return;
    this.executeClose();
  }

  requestPrematureClosure(): void {
    if (!this.requireConfirmation('REQUEST_PREMATURE')) return;
    this.executePrematureRequest();
  }

  approvePrematureClosure(): void {
    if (!this.pendingClosure) {
      this.errorMessage = 'There is no pending premature-closure request to approve.';
      return;
    }
    this.selectedPrematureClosure = this.pendingClosure;
    if (!this.requireConfirmation('APPROVE_PREMATURE')) return;
    this.executePrematureApproval();
  }

  selectPrematureClosure(closure: PrematureClosureResponse): void {
    this.selectedPrematureClosure = closure;
  }

  entryLabel(entryType: string): string {
    const labels: Record<string, string> = {
      FUNDING: 'Funding', ACCRUAL: 'Daily interest accrual', CAPITALIZATION: 'Interest capitalized', MATURITY: 'Maturity',
      CLOSURE: 'Closure', SETTLEMENT: 'Settlement', PENALTY: 'Penalty', TRANSACTION: 'Transaction',
    };
    return labels[entryType.toUpperCase()] ?? 'Account activity';
  }

  entryDescription(entry: HistoryEntry): string {
    const descriptions: Record<string, string> = {
      FUNDING: 'Funding was recorded from your linked savings account.',
      ACCRUAL: 'Interest accrued for this date.',
      CAPITALIZATION: 'Interest was added to the deposit balance.',
      MATURITY: 'This deposit reached maturity.',
      CLOSURE: 'The deposit was closed and proceeds were returned to your linked savings account.',
      SETTLEMENT: 'Proceeds were recorded for return to your linked savings account.',
      PENALTY: 'A premature-closure penalty was recorded.',
      TRANSACTION: 'An account transaction was recorded.',
    };
    return descriptions[entry.entry_type.toUpperCase()] ?? 'Account activity was recorded.';
  }

  journalOperationLabel(operation: string): string {
    const labels: Record<string, string> = {
      FUNDING: 'Deposit funding',
      INTEREST: 'Interest recognition',
      SETTLEMENT: 'Maturity settlement',
    };
    return labels[operation] ?? 'Account entry';
  }

  ledgerAccountLabel(accountCode: string): string {
    const labels: Record<string, string> = {
      SAVINGS_LIABILITY: 'Linked savings account',
      FD_PRINCIPAL_LIABILITY: 'Fixed deposit principal',
      INTEREST_EXPENSE: 'Interest earned',
      ACCRUED_INTEREST_PAYABLE: 'Accrued interest',
      DEMO_CASH: 'Demonstration funds',
    };
    return labels[accountCode] ?? 'Other account';
  }

  formatFrequency(value: string | null | undefined): string {
    if (!value) return '—';
    return value.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
  }

  accountStatusLabel(status: FdStatus): string {
    const labels: Record<FdStatus, string> = {
      PENDING: 'Pending funding',
      ACTIVE: 'Active',
      MATURED: 'Matured',
      CLOSED: 'Closed',
      PREMATURELY_CLOSED: 'Closed early',
    };
    return labels[status];
  }

  statusLabel(status: string): string {
    return status.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
  }

  closureStatusLabel(status: string): string {
    if (status === 'PENDING') return 'Pending';
    if (status === 'APPROVED') return 'Accepted';
    return this.statusLabel(status);
  }

  actionLabel(action: AccountAction): string {
    const labels: Record<AccountAction, string> = {
      FUND: 'funding',
      CLOSE: 'closure',
      REQUEST_PREMATURE: 'premature-closure request',
      APPROVE_PREMATURE: 'early closure',
    };
    return labels[action];
  }

  resultTitle(kind: Result['kind']): string {
    const titles: Record<Result['kind'], string> = {
      funding: 'Funding recorded',
      closure: 'Deposit closed',
      premature: 'Early-closure update',
    };
    return titles[kind];
  }

  private requireConfirmation(action: AccountAction): boolean {
    if (!this.isAllowed(action) && action !== 'APPROVE_PREMATURE') return false;
    if (this.pendingConfirmation === action) return true;
    this.pendingConfirmation = action;
    this.errorMessage = '';
    return false;
  }

  private isAllowed(action: AccountAction): boolean {
    return this.actions.includes(action);
  }

  private journalMatches(journal: Journal | undefined, savingsAccountId: number,
                         currencyCode: string, expectedAmount: number): boolean {
    return !!journal && journal.savings_account_id === savingsAccountId
      && journal.currency_code === currencyCode
      && Math.abs(journal.total_credit - expectedAmount) < 0.00005;
  }

  private executeFunding(): void {
    if (!this.account || !this.isAllowed('FUND')) return;
    const request = { amount: this.account.principal_amount, currency_code: this.account.currency_code };
    this.execute('FUND', () => this.fdService.fund(this.accountId, request), (value) => {
      this.result = { kind: 'funding', value };
      this.successMessage = 'Funding was recorded in the demonstration. No external payment was processed.';
    });
  }

  private executeClose(): void {
    if (!this.isAllowed('CLOSE') || !this.canSettleAtMaturity) return;
    this.execute('CLOSE', () => this.fdService.close(this.accountId), (value) => {
      this.result = { kind: 'closure', value };
      this.successMessage = 'The closure was recorded in the demonstration. No external payment was processed.';
    });
  }

  private executePrematureRequest(): void {
    if (!this.isAllowed('REQUEST_PREMATURE')) return;
    this.execute('REQUEST_PREMATURE', () => this.fdService.requestPrematureClosure(this.accountId), (value) => {
      this.result = { kind: 'premature', value };
      this.successMessage = 'Your premature-closure request was recorded.';
    });
  }

  private executePrematureApproval(): void {
    const closure = this.selectedPrematureClosure;
    if (!closure) return;
    this.execute('APPROVE_PREMATURE', () => this.fdService.approvePrematureClosure(this.accountId, closure.closure_id), (value) => {
      this.result = { kind: 'premature', value };
      this.successMessage = 'The early-closure offer was accepted in the demonstration. No external payment was processed.';
    });
  }

  private execute<T>(action: AccountAction, request: () => import('rxjs').Observable<T>, onSuccess: (value: T) => void): void {
    if (this.busyAction) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.busyAction = action;
    this.fdService.startSubmission();
    request().subscribe({
      next: (value) => {
        this.busyAction = null;
        this.fdService.completeSubmission();
        onSuccess(value);
        this.refreshAfterWrite();
      },
      error: (error: HttpErrorResponse) => {
        this.busyAction = null;
        this.errorMessage = this.messageFor(normalizeApiError(error));
        this.pendingConfirmation = FdAccountDetail.requiresConfirmation(action) ? action : null;
      },
    });
  }

  private loadAccount(): void {
    this.loading = true;
    this.errorMessage = '';
    this.fdService.getAccount(this.accountId).subscribe({
      next: (account) => {
        this.account = account;
        this.loadSavings();
        this.loadJournals();
        this.loading = false;
        this.loadHistory();
        this.loadPrematureClosures();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = this.messageFor(normalizeApiError(error));
      },
    });
  }

  private refreshAfterWrite(): void {
    this.fdService.getAccount(this.accountId).subscribe({
      next: (account) => {
        this.account = account;
        this.loadSavings();
        this.loadJournals();
        this.loadHistory();
        this.loadPrematureClosures();
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.messageFor(normalizeApiError(error)); },
    });
  }

  private loadSavings(): void {
    this.savings = null; this.savingsError = '';
    const id = this.account?.funding_savings_account_id;
    if (!id) return;
    this.fdService.getFundingSavings(this.accountId).subscribe({
      next: account => { this.savings = account; },
      error: (e: HttpErrorResponse) => { this.savingsError = normalizeApiError(e).message; },
    });
  }

  private loadJournals(): void {
    this.journalsLoading = true;
    this.journals = [];
    this.journalError = '';
    this.fdService.listJournals(this.accountId).subscribe({
      next: journals => { this.journals = journals; this.journalsLoading = false; },
      error: (e: HttpErrorResponse) => {
        this.journalError = normalizeApiError(e).message;
        this.journalsLoading = false;
      },
    });
  }

  private loadHistory(): void {
    this.historyLoading = true;
    this.fdService.listTransactions(this.accountId).subscribe({
      next: (history) => { this.history = history; this.historyLoading = false; },
      error: (error: HttpErrorResponse) => {
        this.historyLoading = false;
        this.errorMessage = this.messageFor(normalizeApiError(error));
      },
    });
  }

  private loadPrematureClosures(): void {
    if (!this.account || !['ACTIVE', 'MATURED'].includes(this.account.status)) {
      this.prematureClosures = [];
      return;
    }
    this.fdService.getPrematureClosures(this.accountId).subscribe({
      next: (closures) => { this.prematureClosures = closures; },
      error: () => { this.prematureClosures = []; },
    });
  }

  private messageFor(error: ApiErrorResponse): string {
    if (error.status === 400) return error.message || 'Check the account action details and try again.';
    if (error.status === 401) return 'Your session has expired. Please sign in again.';
    if (error.status === 404) return 'This FD account was not found for your session. No action was recorded.';
    if (error.status === 409) return `${error.message} ${error.replayHint ?? 'The account may have changed; review its current status.'}`;
    if (error.status === 422) return 'Premature closure is not supported for this account or product.';
    return 'The FD service is temporarily unavailable. No success is shown; please retry later.';
  }
}
