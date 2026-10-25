import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { KycService } from '../../../core/services/kyc.service';
import { KycProfile } from '../../../core/models/kyc.models';
import { ringOffset } from '../../kyc-validation';

@Component({
  selector: 'app-kyc-progress-ring', standalone: true, imports: [CommonModule, RouterLink, TranslatePipe],
  template: `
    <div *ngIf="loading" class="kyc-skeleton" role="status">{{ 'Loading KYC...' | translate }}</div>
    <section *ngIf="profile as p" class="kyc-progress" [class.verified]="p.kycStatus === 'VERIFIED'" [class.rejected]="p.kycStatus === 'REJECTED'">
      <svg viewBox="0 0 120 120" role="img" [attr.aria-label]="('KYC completion' | translate) + ': ' + percent + '%'">
        <circle class="track" cx="60" cy="60" r="50" />
        <circle class="arc" cx="60" cy="60" r="50" pathLength="100" stroke-dasharray="100" [attr.stroke-dashoffset]="offset" />
        <text x="60" y="65" text-anchor="middle">{{ percent }}%</text>
      </svg>
      <div><p>{{ (p.kycStatus === 'VERIFIED' ? 'KYC verified' : p.kycStatus === 'REJECTED' ? 'KYC rejected' : 'KYC in progress') | translate }}</p>
        <p *ngIf="p.kycStatus === 'REJECTED'">{{ 'Please consult your bank officer about your rejected KYC.' | translate }}</p>
        <a routerLink="/kyc" class="glass-link">{{ (p.kycStatus !== 'PENDING' ? 'View KYC' : percent === 0 ? 'Start KYC' : 'Continue KYC') | translate }}</a>
      </div>
    </section>`,
  styles: [`
    .kyc-progress { display: flex; align-items: center; gap: 20px; margin-bottom: 28px; color: var(--gold-500); }
    .kyc-progress div { color: var(--ws-text); }
    .kyc-progress.verified { color: var(--mint-500); } .kyc-progress.rejected { color: var(--rose-500); }
    svg { width: 120px; height: 120px; flex-shrink: 0; } circle { fill: none; stroke-width: 8; }
    .track { stroke: var(--ws-line); } .arc { stroke: currentColor; transform: rotate(-90deg); transform-origin: center; transition: stroke-dashoffset 1s ease; }
    text { fill: var(--ws-text); font: 600 20px var(--font-display); }
    .kyc-skeleton { min-height: 120px; margin-bottom: 28px; border-radius: var(--radius-md); background: var(--ws-surface); color: var(--ws-muted); padding: 20px; }
    @media (prefers-reduced-motion: reduce) { .arc { transition: none; } }
  `],
})
export class KycProgressRing implements OnInit, OnDestroy {
  private readonly api = inject(KycService);
  profile: KycProfile | null = null;
  loading = true;
  offset = 100;
  private frame = 0;
  get percent(): number { return this.profile?.kycStatus === 'VERIFIED' ? 100 : this.profile?.completionPercent ?? 0; }
  ngOnInit(): void {
    this.api.get().subscribe({ next: profile => {
      this.profile = profile; this.loading = false;
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) this.offset = ringOffset(this.percent);
      else this.frame = requestAnimationFrame(() => { this.frame = requestAnimationFrame(() => this.offset = ringOffset(this.percent)); });
    }, error: () => { this.loading = false; } });
  }
  ngOnDestroy(): void { cancelAnimationFrame(this.frame); }
}
