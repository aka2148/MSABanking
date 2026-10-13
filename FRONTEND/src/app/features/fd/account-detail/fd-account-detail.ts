import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AccountResponse,
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
      this.errorMessage = 'This FD account link is not valid. Return to your deposits and try again.';
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

  get fundingResult(): FundingResponse | null {
    return this.result?.kind === 'funding' ? this.result.value : null;
  }

  get closureResult(): ClosureResponse | null {
    return this.result?.kind === 'closure' ? this.result.value : null;
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
      FUNDING: 'Funding record', ACCRUAL: 'Daily interest accrual', CAPITALIZATION: 'Interest capitalized', MATURITY: 'Maturity',
      CLOSURE: 'Closure', SETTLEMENT: 'Settlement', PENALTY: 'Penalty',
    };
    return labels[entryType.toUpperCase()] ?? entryType;
  }

  formatFrequency(value: string | null | undefined): string {
    if (!value) return '—';
    return value.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
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

  private executeFunding(): void {
    if (!this.account || !this.isAllowed('FUND')) return;
    const request = { amount: this.account.principal_amount, currency_code: this.account.currency_code };
    this.execute('FUND', () => this.fdService.fund(this.accountId, request), (value) => {
      this.result = { kind: 'funding', value };
      this.successMessage = 'Funding was recorded as an internal academic record; no external payment rail was called.';
    });
  }

  private executeClose(): void {
    if (!this.isAllowed('CLOSE')) return;
    this.execute('CLOSE', () => this.fdService.close(this.accountId), (value) => {
      this.result = { kind: 'closure', value };
      this.successMessage = 'Closure was recorded as an internal academic settlement; no external payment rail was called.';
    });
  }

  private executePrematureRequest(): void {
    if (!this.isAllowed('REQUEST_PREMATURE')) return;
    this.execute('REQUEST_PREMATURE', () => this.fdService.requestPrematureClosure(this.accountId), (value) => {
      this.result = { kind: 'premature', value };
      this.successMessage = 'The premature-closure request was recorded for this academic account.';
    });
  }

  private executePrematureApproval(): void {
    const closure = this.selectedPrematureClosure;
    if (!closure) return;
    this.execute('APPROVE_PREMATURE', () => this.fdService.approvePrematureClosure(this.accountId, closure.closure_id), (value) => {
      this.result = { kind: 'premature', value };
      this.successMessage = 'The premature-closure approval was recorded; no external payment rail was called.';
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
        this.loadHistory();
        this.loadPrematureClosures();
      },
      error: (error: HttpErrorResponse) => { this.errorMessage = this.messageFor(normalizeApiError(error)); },
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
