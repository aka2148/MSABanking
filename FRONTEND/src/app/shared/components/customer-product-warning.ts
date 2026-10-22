import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

export const BLOCKED_RISK = 'HIGH';
@Component({
  selector: 'app-customer-product-warning', standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `<p *ngIf="message" class="glass-error" role="alert">{{ message | translate }}</p>`,
})
export class CustomerProductWarning implements OnInit {
  @Output() blockedChange = new EventEmitter<boolean>();
  private readonly auth = inject(AuthService);
  status = 'UNKNOWN';
  message = 'Checking product access...';
  ngOnInit(): void {
    this.auth.customerProfile().subscribe({
      next: profile => {
        this.status = profile.kycStatus?.toUpperCase() || 'UNKNOWN';
        this.message = this.status !== 'VERIFIED' ? (this.status === 'REJECTED' ? "Your KYC verification was rejected. You can't book fixed deposits, apply for loans or request forex. Please consult your bank officer to resolve the rejection and complete the KYC process." : "Your KYC verification is still pending. You can't book fixed deposits, apply for loans or request forex until it is complete. Please consult your bank officer to complete the KYC process.")
          : profile.riskCategory?.toUpperCase() === BLOCKED_RISK ? "Your risk category needs bank officer review. You can't book fixed deposits, apply for loans or request forex. Please consult your bank officer." : '';
        this.blockedChange.emit(!!this.message);
      },
      error: () => { this.message = "Your KYC verification is still pending. You can't book fixed deposits, apply for loans or request forex until it is complete. Please consult your bank officer to complete the KYC process."; this.blockedChange.emit(true); },
    });
  }
}
