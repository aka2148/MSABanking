import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { normalizeApiError } from '../../core/services/fd.service';
import { CustomerAdminRecord, CustomerAdminService } from '../../core/services/customer-admin.service';
import { LoanApplication, LoanEvent, LoanForexService } from '../../core/services/loan-forex.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';

@Component({
  selector: 'app-loan-admin', standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, WorkspaceShell],
  templateUrl: './loan-admin.html', styleUrl: './loan-page.scss',
})
export class LoanAdmin implements OnInit {
  loading = true; busy = false; error = ''; message = '';
  applications: LoanApplication[] = []; application: LoanApplication | null = null; history: LoanEvent[] = [];
  customer: CustomerAdminRecord | null = null;
  readonly rejectForm;
  private readonly id: number;
  constructor(private readonly api: LoanForexService, private readonly customers: CustomerAdminService, private readonly route: ActivatedRoute, fb: FormBuilder) {
    this.id = Number(route.snapshot.paramMap.get('id'));
    this.rejectForm = fb.nonNullable.group({ reason: ['', [Validators.required, Validators.maxLength(500)]] });
  }
  ngOnInit(): void { this.load(); }
  mask(number: string): string { return `•••• ${number.slice(-4)}`; }
  load(): void {
    this.loading = true;
    if (Number.isInteger(this.id) && this.id > 0) {
      this.api.adminLoan(this.id).subscribe({
        next: item => { this.application = item; this.loading = false; this.api.adminLoanHistory(this.id).subscribe({ next: events => this.history = events, error: e => this.fail(e) }); this.customers.byUser(item.customerId).subscribe({ next: customer => this.customer = customer, error: e => this.fail(e) }); },
        error: e => this.fail(e),
      });
    } else this.api.adminLoans().subscribe({ next: items => { this.applications = items; this.loading = false; }, error: e => this.fail(e) });
  }
  action(kind: 'approve' | 'reject' | 'disburse'): void {
    if (!this.application || this.busy) return;
    if (kind === 'reject' && this.rejectForm.invalid) { this.rejectForm.markAllAsTouched(); this.error = 'Enter a rejection reason.'; return; }
    this.busy = true; this.error = ''; this.message = '';
    const request = kind === 'approve' ? this.api.approve(this.id)
      : kind === 'reject' ? this.api.reject(this.id, this.rejectForm.controls.reason.value.trim())
        : this.api.disburse(this.id);
    request.subscribe({
      next: item => { this.application = item; this.busy = false; this.message = `Application ${item.status.toLowerCase().replace('_',' ')}.`; this.load(); },
      error: e => { this.busy = false; this.error = normalizeApiError(e).message; },
    });
  }
  private fail(error: HttpErrorResponse): void { this.loading = false; this.error = normalizeApiError(error).message; }
}
