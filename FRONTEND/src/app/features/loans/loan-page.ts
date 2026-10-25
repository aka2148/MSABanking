import { CustomerProductWarning } from '../../shared/components/customer-product-warning';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SavingsAccount } from '../../core/models/fd.models';
import { FdService, normalizeApiError } from '../../core/services/fd.service';
import { CustomerOffer, LoanApplication, LoanEvent, LoanForexService } from '../../core/services/loan-forex.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { FormsModule } from '@angular/forms';
import { MpinInput } from '../../shared/components/mpin-input';
import { MpinProofService,mpinErrorMessage } from '../../core/services/mpin-proof.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-loan-page', standalone: true,
  imports: [CustomerProductWarning, CommonModule, ReactiveFormsModule,FormsModule,RouterLink, WorkspaceShell,MpinInput,TranslatePipe],
  templateUrl: './loan-page.html', styleUrl: './loan-page.scss',
})
export class LoanPage implements OnInit {
  productAccessBlocked = true;
  mode: 'catalog' | 'product' | 'applications' | 'detail';
  loading = true; busy = false; error = ''; success = '';
  offers: CustomerOffer[] = []; offer: CustomerOffer | null = null;
  applications: LoanApplication[] = []; application: LoanApplication | null = null;
  history: LoanEvent[] = []; savings: SavingsAccount[] = [];
  private key: string | null = null;
  mpin='';mpinNotSet=false;
  readonly form;

  constructor(private readonly api: LoanForexService, private readonly fd: FdService,private readonly mpinProof:MpinProofService,
      private readonly route: ActivatedRoute, private readonly router: Router, fb: FormBuilder) {
    this.mode = this.route.snapshot.data['mode'] as LoanPage['mode'];
    this.form = fb.nonNullable.group({
      amount: [0, [Validators.required, Validators.min(0.01)]],
      tenureMonths: [0, [Validators.required, Validators.min(1)]],
      currencyCode: ['', Validators.required],
      purpose: ['', [Validators.required, Validators.maxLength(500)]],
      destinationSavingsAccountId: [0, Validators.min(1)],
    });
    this.form.valueChanges.subscribe(() => { this.key = null; });
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (this.mode === 'catalog') this.api.offers('LOAN').subscribe({ next: rows => { this.offers = rows; this.loading = false; }, error: e => this.fail(e) });
    else if (this.mode === 'applications') this.api.loans().subscribe({ next: rows => { this.applications = rows; this.loading = false; }, error: e => this.fail(e) });
    else if (!Number.isInteger(id) || id < 1) { this.loading = false; this.error = 'Invalid loan link.'; }
    else if (this.mode === 'product') {
      this.api.offer('LOAN', id).subscribe({
        next: offer => { this.offer = offer; this.form.controls.currencyCode.setValue(offer.baseCurrencyCode); this.loading = false; },
        error: e => this.fail(e),
      });
      this.fd.listSavings().subscribe({ next: rows => this.savings = rows, error: e => this.fail(e) });
    } else this.api.loan(id).subscribe({
      next: row => { this.application = row; this.loading = false; this.api.loanHistory(id).subscribe({ next: events => this.history = events, error: e => this.fail(e) }); },
      error: e => this.fail(e),
    });
  }

  get eligibleSavings(): SavingsAccount[] { return this.savings.filter(a => a.currency_code === this.form.controls.currencyCode.value); }
  mask(number: string): string { return `•••• ${number.slice(-4)}`; }
  currencyLimits(code: string): CustomerOffer['currencies'][number] | undefined { return this.offer?.currencies.find(c => c.code === code); }
  rateFor(code: string, months: number): number | null {
    return this.offer?.pricingBands.find(b => b.currencyCode === code && months >= b.valueFrom && months <= b.valueTo)?.rate ?? null;
  }
  submit(): void {
    if (this.productAccessBlocked || this.busy || !this.offer) return;
    const value = this.form.getRawValue();
    const limit = this.currencyLimits(value.currencyCode);
    const account = this.savings.find(a => a.account_id === value.destinationSavingsAccountId);
    if (this.form.invalid || !limit || value.amount < limit.minimumAmount || value.amount > limit.maximumAmount
        || this.rateFor(value.currencyCode, value.tenureMonths) == null || !account
        || account.status !== 'ACTIVE' || account.currency_code !== value.currencyCode) {
      this.form.markAllAsTouched(); this.error = 'Check the amount, tenure, purpose, and active destination account against the offer.'; return;
    }
    this.busy = true; this.error = '';
    this.key ??= crypto.randomUUID();
    const body={ productId: this.offer.productId, ...value, purpose: value.purpose.trim() };
    this.mpinProof.requestProof('LOAN_APPLICATION',{productId:body.productId,amount:body.amount,currencyCode:body.currencyCode,tenureMonths:body.tenureMonths,destinationSavingsAccountId:body.destinationSavingsAccountId},this.mpin).subscribe({next:proof=>this.api.apply(body,this.key!,proof.proofToken).subscribe({
      next: application => { this.busy = false; this.key = null;this.mpin=''; void this.router.navigate(['/applications', application.id]); },
      error: e => { this.busy = false;this.mpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET'; this.error = mpinErrorMessage(e); },
    }),error:e=>{this.busy=false;this.mpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET';this.error=mpinErrorMessage(e);}});
  }
  private fail(error: HttpErrorResponse): void { this.loading = false; this.error = normalizeApiError(error).message; }
}
