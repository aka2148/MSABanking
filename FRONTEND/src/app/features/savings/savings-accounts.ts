import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SavingsAccount } from '../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../core/services/fd.service';
import { GlassShell } from '../../shared/components/glass-shell/glass-shell';
import { GlassSelect } from '../../shared/components/glass-select/glass-select';

@Component({
  selector: 'app-savings-accounts', standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, GlassShell, GlassSelect],
  templateUrl: './savings-accounts.html', styleUrl: '../fd/booking/fd-booking.scss',
})
export class SavingsAccounts implements OnInit {
  accounts: SavingsAccount[] = [];
  loading = false; submitting = false; error = ''; message = '';
  demoEnabled = false; demoBusy = false;
  private submission: { body: string; key: string } | null = null;
  readonly currencies = [{ label: 'Indian Rupee (INR)', value: 'INR' }, { label: 'US Dollar (USD)', value: 'USD' }];
  readonly form;
  constructor(private readonly fd: FdService, fb: FormBuilder) {
    this.form = fb.nonNullable.group({
      displayName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/.*\S.*/)]],
      currencyCode: ['INR', Validators.required],
    });
  }
  ngOnInit(): void {
    this.load();
    this.fd.demoOptions().subscribe({ next: options => { this.demoEnabled = options.enabled; }, error: () => {} });
  }
  demoOpening(account: SavingsAccount): void {
    if (!this.demoEnabled || this.demoBusy) return;
    this.demoBusy = true; this.error = '';
    this.fd.provisionDemoOpening(account.account_id, 100000).subscribe({
      next: () => { this.demoBusy = false; this.message = 'Demo opening funds recorded. This action credits each savings account once.'; this.load(); },
      error: (e: HttpErrorResponse) => { this.demoBusy = false; this.error = normalizeApiError(e).message; },
    });
  }
  load(): void {
    this.loading = true;
    this.fd.listSavings().subscribe({
      next: accounts => { this.accounts = accounts; this.loading = false; },
      error: (e: HttpErrorResponse) => { this.error = normalizeApiError(e).message; this.loading = false; },
    });
  }
  create(): void {
    if (this.submitting) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    const request = { display_name: value.displayName.trim(), currency_code: value.currencyCode };
    const body = JSON.stringify(request);
    if (this.submission?.body !== body) this.submission = { body, key: crypto.randomUUID() };
    this.submitting = true; this.error = ''; this.message = '';
    this.fd.createSavings(request, this.submission.key).subscribe({
      next: account => {
        this.submitting = false; this.submission = null;
        this.message = 'Savings account ' + account.account_number + ' created.';
        this.form.controls.displayName.reset(); this.load();
      },
      error: (e: HttpErrorResponse) => { this.submitting = false; this.error = normalizeApiError(e).message; },
    });
  }
}
