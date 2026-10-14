import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { FdClockAdminService, FdClockStatus } from '../../core/services/fd-clock-admin.service';
import { normalizeApiError } from '../../core/services/fd.service';
import { GlassShell } from '../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-dashboard-stub',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, GlassShell],
  template: `
    <app-glass-shell align="center" [focalX]="50" [focalY]="42" [zoom]="1.15">
      <main class="glass-card dashboard-compatibility-card" [class.dashboard-compatibility-card--admin]="isAdmin">
        <p class="dashboard-eyebrow">Authenticated workspace</p>
        <h2>Welcome back</h2>
        <p class="dashboard-copy" *ngIf="canManageProducts; else customerWorkspace">
          Configure customer fixed-deposit products, currencies, and tenure-rate schedules.
        </p>
        <ng-template #customerWorkspace>
          <p class="dashboard-copy">
          Your fixed-deposit workspace is ready. Open it to browse eligible offers or inspect a deposit account.
          </p>
        </ng-template>
        <a class="glass-btn glass-btn--full" *ngIf="!canManageProducts" routerLink="/savings">Savings accounts</a>
        <a class="glass-btn glass-btn--full" *ngIf="!canManageProducts" routerLink="/fd">Open Fixed Deposits</a>
        <a class="glass-btn glass-btn--full" *ngIf="canManageProducts" routerLink="/staff/fd-products">
          Manage FD products
        </a>
        <section class="dashboard-admin-clock" *ngIf="isAdmin" aria-labelledby="admin-clock-heading">
          <header class="dashboard-clock-header">
            <div>
              <p class="dashboard-eyebrow">EOD demonstration</p>
              <h3 id="admin-clock-heading">Simulated system clock</h3>
            </div>
            <span class="dashboard-clock-state" *ngIf="clockStatus">
              {{ clockStatus.simulation_active ? 'SIMULATED' : 'SYSTEM TIME' }}
            </span>
          </header>

          <p class="dashboard-clock-error" *ngIf="clockError" role="alert">{{ clockError }}</p>
          <p class="dashboard-clock-success" *ngIf="clockMessage" role="status">{{ clockMessage }}</p>
          <p class="dashboard-clock-state-text" *ngIf="clockLoading" role="status">Loading clock status…</p>

          <dl class="dashboard-clock-status" *ngIf="clockStatus as clock">
            <div><dt>Business date</dt><dd>{{ clock.business_date }}</dd></div>
            <div><dt>Effective time (IST)</dt><dd>{{ clock.effective_time | date:'medium':'+0530' }}</dd></div>
          </dl>

          <div class="dashboard-clock-actions">
            <label class="glass-field dashboard-clock-days">
              <span class="glass-field__label">Days to advance</span>
              <input type="number" name="clockDays" [(ngModel)]="daysToAdvance"
                     min="1" max="3650" step="1" required [disabled]="!!clockBusy">
            </label>
            <button type="button" class="glass-btn" (click)="advanceClock()"
                    [disabled]="!!clockBusy || daysToAdvance === null">
              {{ clockBusy === 'advance' ? 'Running EOD batch…' : 'Advance and run EOD' }}
            </button>
            <button type="button" class="glass-btn glass-btn--ghost" (click)="resetClock()"
                    [disabled]="!!clockBusy">
              {{ clockBusy === 'reset' ? 'Resetting…' : 'Reset clock' }}
            </button>
          </div>
          <p class="dashboard-clock-note">
            Automatic interest accrual runs at 12 AM IST and maturity processing at 1 AM IST.
            Advance and run EOD processes accrual and maturity immediately for all active FD accounts. Reset clears the simulated time only; saved accrual and history records remain.
          </p>

          <section class="dashboard-batch-result" *ngIf="clockStatus?.batch_run as batch" aria-live="polite">
            <h4>Latest EOD batch</h4>
            <dl>
              <div><dt>Business date</dt><dd>{{ batch.business_date }}</dd></div>
              <div><dt>Active accounts</dt><dd>{{ batch.accounts_examined }}</dd></div>
              <div><dt>Accounts accrued</dt><dd>{{ batch.accruals_processed }}</dd></div>
              <div><dt>Maturities processed</dt><dd>{{ batch.maturities_processed }}</dd></div>
              <div><dt>Failed accounts</dt><dd>{{ batch.accounts_failed }}</dd></div>
            </dl>
            <p *ngIf="batch.accounts_failed > 0">Failed account IDs: {{ batch.failed_account_ids.join(', ') }}</p>
          </section>
        </section>
        <button type="button" class="glass-link dashboard-logout" (click)="logout()">Sign out</button>
      </main>
    </app-glass-shell>
  `,
  styles: [`
    .dashboard-compatibility-card { max-width: 30rem; width: min(100% - 2rem, 30rem); }
    .dashboard-compatibility-card--admin { max-width: 52rem; width: min(100% - 2rem, 52rem); }
    .dashboard-eyebrow { color: var(--gold-500); font: 0.75rem var(--font-mono); letter-spacing: 0.12em; text-transform: uppercase; }
    .dashboard-copy { color: var(--ink-400); line-height: 1.6; margin: 0 0 1.5rem; }
    .dashboard-compatibility-card > .glass-btn { box-sizing: border-box; display: flex; justify-content: center; margin-top: .75rem; text-align: center; }
    .dashboard-logout { background: none; border: 0; display: block; margin: 1rem auto 0; }
    .dashboard-admin-clock { margin-top: 1.5rem; padding: 1.25rem; border: 1px solid rgba(255,255,255,.18); border-radius: var(--radius-md); background: rgba(255,255,255,.045); }
    .dashboard-clock-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
    .dashboard-clock-header h3, .dashboard-batch-result h4 { margin: 0; font-family: var(--font-display); }
    .dashboard-clock-state { color: var(--gold-100); font: var(--fs-xs) var(--font-mono); }
    .dashboard-clock-status, .dashboard-batch-result dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; margin: 1rem 0; }
    .dashboard-clock-status div, .dashboard-batch-result dl div { display: flex; justify-content: space-between; gap: .75rem; padding: .65rem .75rem; border-radius: var(--radius-sm); background: rgba(255,255,255,.06); }
    .dashboard-clock-status dt, .dashboard-batch-result dt { color: var(--ink-400); }
    .dashboard-clock-status dd, .dashboard-batch-result dd { margin: 0; text-align: right; font-family: var(--font-mono); }
    .dashboard-clock-actions { display: grid; grid-template-columns: minmax(120px, .7fr) 1fr 1fr; align-items: end; gap: .75rem; }
    .dashboard-clock-days { margin: 0; }
    .dashboard-clock-note, .dashboard-clock-state-text { color: var(--ink-400); font-size: var(--fs-xs); line-height: 1.5; }
    .dashboard-clock-error, .dashboard-clock-success { padding: .65rem .75rem; border-radius: var(--radius-sm); }
    .dashboard-clock-error { color: #ffe0e0; background: rgba(120,25,25,.2); }
    .dashboard-clock-success { color: #d5fff0; background: rgba(31,138,112,.14); }
    .dashboard-batch-result { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid rgba(255,255,255,.15); }
    .dashboard-batch-result dl { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    @media (max-width: 700px) {
      .dashboard-clock-actions, .dashboard-batch-result dl { grid-template-columns: 1fr; }
      .dashboard-clock-actions .glass-btn { width: 100%; }
    }
  `],
})
export class DashboardStub implements OnInit {
  daysToAdvance: number | null = 1;
  clockStatus: FdClockStatus | null = null;
  clockLoading = false;
  clockBusy: 'advance' | 'reset' | null = null;
  clockError = '';
  clockMessage = '';

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly clockService: FdClockAdminService,
  ) {}

  ngOnInit(): void {
    if (this.isAdmin) this.loadClockStatus();
  }

  get isAdmin(): boolean {
    return this.auth.currentRole() === 'ROLE_ADMIN';
  }

  get canManageProducts(): boolean {
    return ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(this.auth.currentRole() ?? '');
  }

  advanceClock(): void {
    const days = this.daysToAdvance;
    if (!this.isAdmin || this.clockBusy) return;
    if (days === null || !Number.isInteger(days) || days < 1 || days > 3650) {
      this.clockError = 'Enter a whole number of days from 1 to 3650.';
      return;
    }

    this.clockBusy = 'advance';
    this.clockError = '';
    this.clockMessage = '';
    this.clockService.advance(days).subscribe({
      next: (status) => {
        this.clockStatus = status;
        this.clockBusy = null;
        this.clockMessage = status.batch_run
          ? `EOD batch ran for ${status.batch_run.business_date}.`
          : `Clock advanced to ${status.business_date}.`;
      },
      error: (error: HttpErrorResponse) => {
        this.clockBusy = null;
        this.clockError = this.clockErrorMessage(error);
      },
    });
  }

  resetClock(): void {
    if (!this.isAdmin || this.clockBusy) return;
    this.clockBusy = 'reset';
    this.clockError = '';
    this.clockMessage = '';
    this.clockService.reset().subscribe({
      next: (status) => {
        this.clockStatus = status;
        this.clockBusy = null;
        this.clockMessage = 'Simulated clock reset. Saved accrual history was not changed.';
      },
      error: (error: HttpErrorResponse) => {
        this.clockBusy = null;
        this.clockError = this.clockErrorMessage(error);
      },
    });
  }

  private loadClockStatus(): void {
    this.clockLoading = true;
    this.clockService.getStatus().subscribe({
      next: (status) => {
        this.clockStatus = status;
        this.clockLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        this.clockLoading = false;
        this.clockError = this.clockErrorMessage(error);
      },
    });
  }

  private clockErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Your admin session expired. Sign in again.';
    if (error.status === 403) return 'Clock controls are available to ADMIN users only.';
    return normalizeApiError(error).message || 'The clock request could not be completed.';
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
