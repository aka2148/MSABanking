import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SavingsAccount } from '../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../core/services/fd.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { GlassSelect } from '../../shared/components/glass-select/glass-select';

@Component({
  selector: 'app-savings-accounts', standalone: true,
  imports: [TranslatePipe, CommonModule, ReactiveFormsModule, RouterLink, WorkspaceShell, GlassSelect],
  templateUrl: './savings-accounts.html', styleUrl: './savings-accounts.scss',
})
export class SavingsAccounts implements OnInit {
  accounts: SavingsAccount[] = [];
  loading = false; submitting = false; error = ''; message = '';
  messageParameters: readonly unknown[] = [];
  demoEnabled = false; demoBusy = false;
  currenciesLoading = true;
  private submission: { body: string; key: string } | null = null;
  currencies: Array<{ label: string; value: string }> = [];
  readonly form;
  constructor(private readonly fd: FdService, fb: FormBuilder) {
    this.form = fb.nonNullable.group({
      displayName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/.*\S.*/)]],
      currencyCode: ['INR', Validators.required],
    });
  }
  ngOnInit(): void {
    this.load();
    this.fd.listSavingsCurrencies().subscribe({
      next: currencies => {
        this.currencies = currencies.map(currency => ({
          label: `${currency.currencyName} (${currency.currencyCode}) · ${currency.decimalPlaces} decimals`,
          value: currency.currencyCode,
        }));
        const initial = currencies.find(currency => currency.currencyCode === 'USD')?.currencyCode
          ?? currencies.find(currency => currency.currencyCode === 'INR')?.currencyCode
          ?? currencies[0]?.currencyCode ?? '';
        this.form.controls.currencyCode.setValue(initial);
        this.currenciesLoading = false;
        if (!currencies.length) this.error = 'No currencies have reached the FD service yet. Wait for a product catalog update, then refresh.';
      },
      error: (e: HttpErrorResponse) => {
        this.error = normalizeApiError(e).message;
        this.currenciesLoading = false;
      },
    });
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
  statusLabel(status: string): string {
    return status.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
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
    if (!this.currencies.length || this.currenciesLoading) {
      this.error = 'No currencies are available for savings accounts yet. Refresh after the product catalog loads.';
      return;
    }
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    const request = { display_name: value.displayName.trim(), currency_code: value.currencyCode };
    const body = JSON.stringify(request);
    if (this.submission?.body !== body) this.submission = { body, key: crypto.randomUUID() };
    this.submitting = true; this.error = ''; this.message = '';
    this.fd.createSavings(request, this.submission.key).subscribe({
      next: account => {
        this.submitting = false; this.submission = null;
        this.message = 'Savings account {0} created.';
        this.messageParameters = [account.account_number];
        this.form.controls.displayName.reset(); this.load();
      },
      error: (e: HttpErrorResponse) => { this.submitting = false; this.error = normalizeApiError(e).message; },
    });
  }
}
