import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';

@Component({
  selector: 'app-dashboard-stub',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, WorkspaceShell],
  template: `
    <app-workspace-shell>
      <main class="workspace-home">
        <header class="workspace-home__header">
          <p class="fd-eyebrow"> {{ 'MoneyPal workspace' | translate }} </p>
          <h1> {{ (canManageProducts ? 'Staff workspace' : 'Welcome back') | translate }} </h1>
          <p> {{ (canManageProducts ? 'Choose an area to continue.' : 'Manage savings and fixed deposits.') | translate }} </p>
        </header>

        <nav class="workspace-home__links" [attr.aria-label]="'Workspace shortcuts' | translate">
          <a *ngIf="!canManageProducts" routerLink="/savings"><span> {{ 'Savings accounts' | translate }} </span><small> {{ 'Open and manage savings' | translate }} </small></a>
          <a *ngIf="!canManageProducts" routerLink="/fd"><span> {{ 'Fixed deposits' | translate }} </span><small> {{ 'Browse eligible offers' | translate }} </small></a>
          <a *ngIf="!canManageProducts" routerLink="/fd/accounts"><span> {{ 'My deposits' | translate }} </span><small> {{ 'View account activity' | translate }} </small></a>
          <a *ngIf="canManageProducts" routerLink="/staff/product-builder"><span> {{ 'Product configuration' | translate }} </span><small> {{ 'Create products from templates or a custom setup' | translate }} </small></a>
          <a *ngIf="canManageProducts" routerLink="/staff/fd-products"><span> {{ 'FD offer management' | translate }} </span><small> {{ 'Activate and update existing fixed-deposit offers' | translate }} </small></a>
          <a *ngIf="canManageProducts" routerLink="/staff/customers"><span> {{ 'Customers' | translate }} </span><small> {{ 'Profiles and KYC status' | translate }} </small></a>
          <a *ngIf="canManageProducts" routerLink="/staff/fd"><span> {{ 'FD accounts' | translate }} </span><small> {{ 'Deposit status and maturity' | translate }} </small></a>
          <a *ngIf="canManageProducts" routerLink="/staff/savings"><span> {{ 'Savings operations' | translate }} </span><small> {{ 'Balances and teller activity' | translate }} </small></a>
          <a *ngIf="canManageProducts" routerLink="/compliance"><span> {{ 'Compliance' | translate }} </span><small> {{ 'Rates and effective dates' | translate }} </small></a>
          <a *ngIf="isAdmin" routerLink="/staff/operations/clock"><span> {{ 'Operations' | translate }} </span><small> {{ 'Administrative tools' | translate }} </small></a>
        </nav>

        <button type="button" class="workspace-home__signout" (click)="logout()"> {{ 'Sign out' | translate }} </button>
      </main>
    </app-workspace-shell>
  `,
  styles: [`
    .workspace-home { width: min(100%, 1040px); margin: 0 auto; }
    .workspace-home__header { margin: 0 0 28px; }
    .workspace-home__header h1 { margin: 0; font: 600 2rem/1.15 var(--font-display); letter-spacing: -.025em; }
    .workspace-home__header p:last-child { margin: 8px 0 0; color: var(--ws-muted); }
    .workspace-home__links { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 10px; }
    .workspace-home__links a { display: flex; min-height: 72px; flex-direction: column; justify-content: center; gap: 4px; padding: 12px 16px; border: 1px solid var(--ws-line); border-radius: 10px; background: var(--ws-surface); color: var(--ws-text); text-decoration: none; transition: border-color 140ms ease, background 140ms ease; }
    .workspace-home__links a:hover { border-color: rgba(229,187,104,.55); background: var(--ws-surface-raised); }
    .workspace-home__links span { font: 600 1rem var(--font-display); }
    .workspace-home__links small { color: var(--ws-muted); font-size: .8rem; }
    .workspace-home__signout { margin-top: 24px; padding: 8px 0; border: 0; background: transparent; color: var(--ws-muted); text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }
    .workspace-home__signout:hover { color: var(--ws-text); }
    @media (max-width: 640px) { .workspace-home__header { margin-bottom: 20px; } .workspace-home__header h1 { font-size: 1.75rem; } }
  `],
})
export class DashboardStub {
  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  get isAdmin(): boolean { return this.auth.currentRole() === 'ROLE_ADMIN'; }
  get canManageProducts(): boolean { return ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(this.auth.currentRole() ?? ''); }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
