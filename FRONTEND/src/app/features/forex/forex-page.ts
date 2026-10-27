import { CustomerProductWarning } from '../../shared/components/customer-product-warning';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FdService, normalizeApiError } from '../../core/services/fd.service';
import { SavingsAccount } from '../../core/models/fd.models';
import { CustomerOffer, ForexQuote, ForexRequest, LoanForexService } from '../../core/services/loan-forex.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { FormsModule } from '@angular/forms';
import { MpinInput } from '../../shared/components/mpin-input';
import { MpinProofService,classifyMpinError } from '../../core/services/mpin-proof.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { GlassSelect } from '../../shared/components/glass-select/glass-select';

@Component({ selector: 'app-forex-page', standalone: true,
  imports: [CustomerProductWarning, CommonModule, ReactiveFormsModule,FormsModule,RouterLink, WorkspaceShell,MpinInput,TranslatePipe,GlassSelect],
  templateUrl: './forex-page.html', styleUrl: '../loans/loan-page.scss' })
export class ForexPage implements OnInit {
  productAccessBlocked = true;
  mode: 'catalog' | 'product' | 'requests' | 'detail';
  loading = true; busy = false; quoteBusy = false; error = '';
  offers: CustomerOffer[] = []; offer: CustomerOffer | null = null;
  savingsAccounts: SavingsAccount[] = [];
  requests: ForexRequest[] = []; request: ForexRequest | null = null; quote: ForexQuote | null = null;
  private key: string | null = null;
  mpin='';mpinNotSet=false;
  readonly form;
  constructor(private readonly api: LoanForexService,private readonly fd: FdService,private readonly mpinProof:MpinProofService, route: ActivatedRoute, private readonly router: Router, fb: FormBuilder) {
    this.mode = route.snapshot.data['mode'] as ForexPage['mode'];
    this.id = Number(route.snapshot.paramMap.get('id'));
    this.form = fb.nonNullable.group({ amount: [0, [Validators.required, Validators.min(0.01)]], destinationCurrencyCode: ['', Validators.required], sourceSavingsAccountId: ['', Validators.required], destinationSavingsAccountId: ['', Validators.required] });
    this.form.valueChanges.subscribe(() => { this.key = null; this.quote = null; });
    this.form.controls.destinationCurrencyCode.valueChanges.subscribe(() => this.setAccountDefaults());
  }
  private readonly id: number;
  get currencyOptions(){return this.offer?.currencies.map(c=>({value:c.code,label:c.code,disabled:c.code===this.offer?.baseCurrencyCode}))??[];}
  ngOnInit(): void {
    if (this.mode === 'catalog') this.api.offers('FOREX').subscribe({ next: rows => { this.offers = rows; this.loading = false; }, error: e => this.fail(e) });
    else if (this.mode === 'requests') this.api.forexRequests().subscribe({ next: rows => { this.requests = rows; this.loading = false; }, error: e => this.fail(e) });
    else if (!Number.isInteger(this.id) || this.id < 1) { this.loading = false; this.error = 'Invalid forex link.'; }
    else if (this.mode === 'product') this.api.offer('FOREX', this.id).subscribe({
      next: offer => {
        this.offer = offer;
        this.form.controls.destinationCurrencyCode.setValue(offer.currencies.find(c => c.code !== offer.baseCurrencyCode)?.code ?? '');
        this.fd.listSavings().subscribe({
          next: accounts => { this.savingsAccounts = accounts; this.loading = false; this.setAccountDefaults(); },
          error: e => this.fail(e),
        });
      },
      error: e => this.fail(e),
    });
    else this.api.forexRequest(this.id).subscribe({ next: row => { this.request = row; this.loading = false; }, error: e => this.fail(e) });
  }
  get markup(): number | null {
    const value = this.form.getRawValue();
    return this.offer?.pricingBands.find(b => b.currencyCode === value.destinationCurrencyCode && value.amount >= b.valueFrom && value.amount <= b.valueTo)?.rate ?? null;
  }
  get fundingAccounts(): SavingsAccount[] {
    const currency = this.offer?.baseCurrencyCode.toUpperCase();
    return this.savingsAccounts.filter(account => account.status === 'ACTIVE' && account.currency_code.toUpperCase() === currency);
  }
  get destinationAccounts(): SavingsAccount[] {
    const currency = this.form.controls.destinationCurrencyCode.value.toUpperCase();
    return this.savingsAccounts.filter(account => account.status === 'ACTIVE' && account.currency_code.toUpperCase() === currency);
  }
  get fundingAccountOptions() { return this.fundingAccounts.map(account => ({ value: String(account.account_id), label: this.accountLabel(account) })); }
  get destinationAccountOptions() { return this.destinationAccounts.map(account => ({ value: String(account.account_id), label: this.accountLabel(account) })); }
  get canRequestQuote(): boolean {
    const value = this.form.getRawValue();
    const base = this.offer?.currencies.find(c => c.code === this.offer?.baseCurrencyCode);
    return !!this.offer && !this.loading && !this.quoteBusy && !!value.sourceSavingsAccountId && !!value.destinationSavingsAccountId
      && value.sourceSavingsAccountId !== value.destinationSavingsAccountId && !!base && value.amount >= base.minimumAmount
      && value.amount <= base.maximumAmount && this.markup != null;
  }
  requestStatus(status: ForexRequest['status']): string {
    return status === 'COMPLETED' ? 'Conversion completed' : status === 'REQUESTED' ? 'Recorded (no approval required)' : status;
  }
  requestQuote(): void {
    if (!this.canRequestQuote || !this.offer) return;
    const value = this.form.getRawValue();
    this.quoteBusy = true; this.error = '';
    this.api.quoteForex({
      productId: this.offer.productId, amount: value.amount,
      fundingCurrencyCode: this.offer.baseCurrencyCode, destinationCurrencyCode: value.destinationCurrencyCode,
      sourceSavingsAccountId: Number(value.sourceSavingsAccountId), destinationSavingsAccountId: Number(value.destinationSavingsAccountId),
    }).subscribe({
      next: quote => { this.quote = quote; this.quoteBusy = false; },
      error: e => { this.quoteBusy = false; this.error = normalizeApiError(e).message; },
    });
  }
  submit(): void {
    const quote = this.quote;
    if (this.productAccessBlocked || this.busy || this.loading || !this.offer || !quote) return;
    const value = this.form.getRawValue();
    const body={ productId: this.offer.productId, amount: value.amount, fundingCurrencyCode: this.offer.baseCurrencyCode,
      destinationCurrencyCode: value.destinationCurrencyCode, sourceSavingsAccountId: Number(value.sourceSavingsAccountId),
      destinationSavingsAccountId: Number(value.destinationSavingsAccountId), referenceRate: quote.referenceRate,
      markupRate: quote.markupRate, customerRate: quote.customerRate, fee: quote.fee,
      totalDebit: quote.totalDebit, destinationAmount: quote.destinationAmount };
    this.busy = true; this.error = ''; this.key ??= crypto.randomUUID();
    this.mpinProof.requestProof('FOREX_REQUEST',body,this.mpin).subscribe({next:proof=>this.api.requestForex(body,this.key!,proof.proofToken).subscribe({
      next: row => { this.busy = false; this.key = null;this.mpin=''; void this.router.navigate(['/forex/requests', row.id]); },
      error: e => { this.busy = false;this.mpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET'; this.error = classifyMpinError('operation',e); },
    }),error:e=>{this.busy=false;this.mpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET';this.error=classifyMpinError('verify',e);}});
  }
  private setAccountDefaults(): void {
    const sourceId = this.form.controls.sourceSavingsAccountId.value;
    if (!this.fundingAccounts.some(account => String(account.account_id) === sourceId))
      this.form.controls.sourceSavingsAccountId.setValue(this.fundingAccounts.length === 1 ? String(this.fundingAccounts[0].account_id) : '');
    const destinationId = this.form.controls.destinationSavingsAccountId.value;
    if (!this.destinationAccounts.some(account => String(account.account_id) === destinationId))
      this.form.controls.destinationSavingsAccountId.setValue(this.destinationAccounts.length === 1 ? String(this.destinationAccounts[0].account_id) : '');
  }
  private accountLabel(account: SavingsAccount): string {
    return `${account.display_name} · ${account.currency_code} · ••••${account.account_number.slice(-4)} · ${account.balance}`;
  }
  private fail(error: HttpErrorResponse): void { this.loading = false; this.error = normalizeApiError(error).message; }
}
