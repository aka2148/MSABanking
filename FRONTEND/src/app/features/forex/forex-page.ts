import { CustomerProductWarning } from '../../shared/components/customer-product-warning';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { normalizeApiError } from '../../core/services/fd.service';
import { CustomerOffer, ForexRequest, LoanForexService } from '../../core/services/loan-forex.service';
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
  loading = true; busy = false; error = '';
  offers: CustomerOffer[] = []; offer: CustomerOffer | null = null;
  requests: ForexRequest[] = []; request: ForexRequest | null = null;
  private key: string | null = null;
  mpin='';mpinNotSet=false;
  readonly form;
  constructor(private readonly api: LoanForexService,private readonly mpinProof:MpinProofService, route: ActivatedRoute, private readonly router: Router, fb: FormBuilder) {
    this.mode = route.snapshot.data['mode'] as ForexPage['mode'];
    this.id = Number(route.snapshot.paramMap.get('id'));
    this.form = fb.nonNullable.group({ amount: [0, [Validators.required, Validators.min(0.01)]], destinationCurrencyCode: ['', Validators.required] });
    this.form.valueChanges.subscribe(() => { this.key = null; });
  }
  private readonly id: number;
  get currencyOptions(){return this.offer?.currencies.map(c=>({value:c.code,label:c.code,disabled:c.code===this.offer?.baseCurrencyCode}))??[];}
  ngOnInit(): void {
    if (this.mode === 'catalog') this.api.offers('FOREX').subscribe({ next: rows => { this.offers = rows; this.loading = false; }, error: e => this.fail(e) });
    else if (this.mode === 'requests') this.api.forexRequests().subscribe({ next: rows => { this.requests = rows; this.loading = false; }, error: e => this.fail(e) });
    else if (!Number.isInteger(this.id) || this.id < 1) { this.loading = false; this.error = 'Invalid forex link.'; }
    else if (this.mode === 'product') this.api.offer('FOREX', this.id).subscribe({
      next: offer => { this.offer = offer; this.form.controls.destinationCurrencyCode.setValue(offer.currencies.find(c => c.code !== offer.baseCurrencyCode)?.code ?? ''); this.loading = false; },
      error: e => this.fail(e),
    });
    else this.api.forexRequest(this.id).subscribe({ next: row => { this.request = row; this.loading = false; }, error: e => this.fail(e) });
  }
  get markup(): number | null {
    const value = this.form.getRawValue();
    return this.offer?.pricingBands.find(b => b.currencyCode === value.destinationCurrencyCode && value.amount >= b.valueFrom && value.amount <= b.valueTo)?.rate ?? null;
  }
  submit(): void {
    if (this.productAccessBlocked || this.busy || !this.offer) return;
    const value = this.form.getRawValue();
    const base = this.offer.currencies.find(c => c.code === this.offer?.baseCurrencyCode);
    if (this.form.invalid || !base || value.amount < base.minimumAmount || value.amount > base.maximumAmount || this.markup == null) {
      this.error = 'Choose a supported destination currency and enter an amount within the offered range.'; this.form.markAllAsTouched(); return;
    }
    this.busy = true; this.error = ''; this.key ??= crypto.randomUUID();
    const body={ productId: this.offer.productId, amount: value.amount, fundingCurrencyCode: base.code, destinationCurrencyCode: value.destinationCurrencyCode };
    this.mpinProof.requestProof('FOREX_REQUEST',body,this.mpin).subscribe({next:proof=>this.api.requestForex(body,this.key!,proof.proofToken).subscribe({
      next: row => { this.busy = false; this.key = null;this.mpin=''; void this.router.navigate(['/forex/requests', row.id]); },
      error: e => { this.busy = false;this.mpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET'; this.error = classifyMpinError('operation',e); },
    }),error:e=>{this.busy=false;this.mpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET';this.error=classifyMpinError('verify',e);}});
  }
  private fail(error: HttpErrorResponse): void { this.loading = false; this.error = normalizeApiError(error).message; }
}
