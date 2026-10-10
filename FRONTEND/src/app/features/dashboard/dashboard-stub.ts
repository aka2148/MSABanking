import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { GlassShell } from '../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-dashboard-stub',
  standalone: true,
  imports: [CommonModule, RouterLink, GlassShell],
  template: `
    <app-glass-shell align="center" [focalX]="50" [focalY]="42" [zoom]="1.15">
      <main class="glass-card dashboard-compatibility-card">
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
        <a class="glass-btn glass-btn--full" *ngIf="!canManageProducts" routerLink="/fd">Open Fixed Deposits</a>
        <a class="glass-btn glass-btn--full" *ngIf="canManageProducts" routerLink="/staff/fd-products">
          Manage FD products
        </a>
        <button type="button" class="glass-link dashboard-logout" (click)="logout()">Sign out</button>
      </main>
    </app-glass-shell>
  `,
  styles: [`
    .dashboard-compatibility-card { max-width: 30rem; width: min(100% - 2rem, 30rem); }
    .dashboard-eyebrow { color: var(--gold-500); font: 0.75rem var(--font-mono); letter-spacing: 0.12em; text-transform: uppercase; }
    .dashboard-copy { color: var(--ink-400); line-height: 1.6; margin: 0 0 1.5rem; }
    .dashboard-logout { background: none; border: 0; display: block; margin: 1rem auto 0; }
  `],
})
export class DashboardStub {
  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  get canManageProducts(): boolean {
    return ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(this.auth.currentRole() ?? '');
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
