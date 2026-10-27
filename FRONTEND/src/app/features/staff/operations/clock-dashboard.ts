import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FdClockAdminService, FdClockStatus } from '../../../core/services/fd-clock-admin.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

@Component({
  selector: 'app-clock-dashboard', standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  template: `
    <app-workspace-shell><main class="glass-card glass-card--wide ops-card">
      <a routerLink="/dashboard" class="glass-link"> {{ '← Staff overview' | translate }} </a>
      <header class="ops-head"><div><p class="fd-eyebrow"> {{ 'Admin operations' | translate }} </p><h2> {{ 'Business clock & end of day' | translate }} </h2><p class="glass-card__sub"> {{ 'Move the demo business date forward and run interest accrual and maturity processing.' | translate }} </p></div><span class="fd-admin-badge"> {{ 'ADMIN ONLY' | translate }} </span></header>
      <p class="glass-error" *ngIf="error" role="alert">{{ error | translate }}</p><p class="ops-success" *ngIf="message" role="status">{{ message | translate }}</p>
      <p class="fd-state" *ngIf="loading" role="status"> {{ 'Loading clock status…' | translate }} </p>
      <section class="ops-panel" *ngIf="status as clock">
        <div class="ops-panel__title"><div><p class="fd-eyebrow"> {{ 'Current state' | translate }} </p><h3> {{ (clock.simulation_active ? 'Simulated time is active' : 'Using system time') | translate }} </h3></div><span class="ops-chip">{{ clock.business_zone }}</span></div>
        <dl class="ops-facts"><div><dt> {{ 'Business date' | translate }} </dt><dd>{{ clock.business_date | date:'longDate':'UTC' }}</dd></div><div><dt> {{ 'Effective time' | translate }} </dt><dd>{{ clock.effective_time | date:'medium':'UTC' }}</dd></div><div><dt> {{ 'System time' | translate }} </dt><dd>{{ clock.system_time | date:'medium':'UTC' }}</dd></div></dl>
      </section>
      <section class="ops-actions"><label class="glass-field" for="clock-advance-days"><span class="glass-field__label"> {{ 'Advance business date by (days)' | translate }} </span><input id="clock-advance-days" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="4" autocomplete="off" [ngModel]="daysInput" (ngModelChange)="daysInput = $event" name="days" [disabled]="busy !== ''" /></label><button class="glass-btn" (click)="advance()" [disabled]="busy !== '' || !canAdvance"> {{ (busy === 'advance' ? 'Running EOD…' : 'Advance and run EOD') | translate }} </button><button class="glass-btn glass-btn--ghost" (click)="reset()" [disabled]="busy !== ''"> {{ (busy === 'reset' ? 'Resetting…' : 'Reset simulated time') | translate }} </button></section>
      <p class="ops-note"> {{ 'Advancing multiple days records daily accruals and capitalization through the selected business date. An FD that reaches maturity is matured on its maturity date. The customer completes the separate close action to transfer the matured funds to savings. Reset clears simulated time; saved accrual and transaction history remain.' | translate }} </p>
      <section class="ops-panel" *ngIf="status?.batch_run as batch"><p class="fd-eyebrow"> {{ 'Latest batch · {0}' | translate: [(batch.business_date | date:'longDate')] }} </p><dl class="ops-facts"><div><dt> {{ 'Accounts examined' | translate }} </dt><dd>{{ batch.accounts_examined | number }}</dd></div><div><dt>Accounts with accruals</dt><dd>{{ batch.accruals_processed | number }}</dd></div><div><dt>Daily accrual entries</dt><dd>{{ batch.accrual_days_recorded | number }}</dd></div><div><dt> {{ 'Maturities processed' | translate }} </dt><dd>{{ batch.maturities_processed | number }}</dd></div><div><dt> {{ 'Failures' | translate }} </dt><dd>{{ batch.accounts_failed | number }}</dd></div></dl><p *ngIf="batch.accounts_failed"> {{ 'Failed account IDs: {0}' | translate: [(batch.failed_account_ids.join(', '))] }} </p></section>
    </main></app-workspace-shell>`,
  styles: [`.ops-card{box-sizing:border-box;width:100%;max-width:1080px;margin:24px}.ops-head,.ops-panel__title,.ops-actions{display:flex;align-items:center;justify-content:space-between;gap:18px}.ops-head{margin:20px 0 26px;align-items:flex-start}.ops-head h2{margin:0;font:600 var(--fs-xl) var(--font-display)}.ops-panel{padding:22px;margin:18px 0;border:1px solid var(--ws-line);border-radius:14px;background:var(--ws-surface-raised)}.ops-panel h3{margin:0;font:600 1.2rem var(--font-display)}.ops-chip{padding:7px 10px;border-radius:99px;background:rgba(214,174,88,.14);color:var(--gold-500);font: .75rem var(--font-mono)}.ops-facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(165px,1fr));gap:18px;margin:22px 0 0}.ops-facts div{display:grid;gap:6px}.ops-facts dt{color:var(--ink-400);font-size:.8rem}.ops-facts dd{margin:0;color:white;font-variant-numeric:tabular-nums}.ops-actions{justify-content:flex-start;flex-wrap:wrap;padding:20px;border:1px solid var(--ws-line);border-radius:14px}.ops-actions .glass-field{min-width:210px}.ops-note{color:var(--ink-400);line-height:1.6}.ops-success{color:#b6e2bf}@media(max-width:650px){.ops-card{margin:12px}.ops-head,.ops-panel__title{align-items:flex-start;flex-direction:column}.ops-actions>*{width:100%}}`],
})
export class ClockDashboard implements OnInit {
  status: FdClockStatus | null = null; daysInput = '1'; loading = false; busy: '' | 'advance' | 'reset' = ''; error = ''; message = '';
  get canAdvance(): boolean { const days = Number(this.daysInput); return /^\d{1,4}$/.test(this.daysInput) && Number.isInteger(days) && days >= 1 && days <= 3650; }
  constructor(private readonly clock: FdClockAdminService) {}
  ngOnInit(): void { this.load(); }
  load(): void { this.loading = true; this.error = ''; this.clock.getStatus().subscribe({ next: s => { this.status = s; this.loading = false; }, error: e => { this.error = this.errorText(e); this.loading = false; } }); }
  advance(): void { this.run('advance'); }
  reset(): void { this.run('reset'); }
  private run(action: 'advance' | 'reset'): void { this.busy = action; this.error = ''; this.message = ''; const request = action === 'advance' ? this.clock.advance(Number(this.daysInput)) : this.clock.reset(); request.subscribe({ next: s => { this.status = s; this.busy = ''; this.message = action === 'advance' ? `EOD completed for ${s.business_date}.` : 'The simulated clock was reset.'; }, error: e => { this.error = this.errorText(e); this.busy = ''; } }); }
  private errorText(e: HttpErrorResponse): string { if (e.status === 401) return 'Your session expired. Sign in again.'; if (e.status === 403) return 'This operation is available to administrators only.'; return e.error?.message ?? 'Could not update the demo clock.'; }
}
