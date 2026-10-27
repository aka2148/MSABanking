import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SavingsAccount } from '../../core/models/fd.models';
import { FdService, normalizeApiError, SavingsHistory, SavingsTransferResponse, SavingsWithdrawalResponse } from '../../core/services/fd.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { GlassSelect } from '../../shared/components/glass-select/glass-select';
import { MpinInput } from '../../shared/components/mpin-input';
import { MpinProofService, classifyMpinError } from '../../core/services/mpin-proof.service';

@Component({
  selector: 'app-savings-accounts', standalone: true,
  imports: [TranslatePipe, CommonModule, ReactiveFormsModule, RouterLink, WorkspaceShell, GlassSelect, MpinInput],
  templateUrl: './savings-accounts.html', styleUrl: './savings-accounts.scss',
})
export class SavingsAccounts implements OnInit {
  accounts: SavingsAccount[] = [];
  loading = false; submitting = false; error = ''; message = '';
  messageParameters: readonly unknown[] = [];
  transferBusy = false;
  transferError = '';
  transferMessage = '';
  transferReference = '';
  transferOptions: Array<{ label: string; value: string }> = [];
  transferHistory: SavingsHistory[] = [];
  withdrawalBusy=false; withdrawalError=''; withdrawalMessage=''; withdrawalReference=''; withdrawalOptions:Array<{label:string;value:string}>=[]; withdrawalForm;
  private withdrawalSubmission:{signature:string;key:string}|null=null;
  mpinNotSet=false;mpinAttempts:string|null=null;mpinLockedUntil:string|null=null;
  historyAccountId: number | null = null;
  currenciesLoading = true;
  private submission: { body: string; key: string } | null = null;
  currencies: Array<{ label: string; value: string }> = [];
  readonly form;
  readonly transferForm;
  private transferSubmission: { signature: string; key: string } | null = null;
  constructor(private readonly fd: FdService, private readonly mpin:MpinProofService,fb: FormBuilder) {
    this.form = fb.nonNullable.group({
      displayName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/.*\S.*/)]],
      currencyCode: ['INR', Validators.required],
    });
    this.transferForm = fb.group({
      fromAccountId: ['', Validators.required],
      toAccountNumber: ['', [Validators.required, Validators.maxLength(64)]],
      amount: [null as number | null, [Validators.required, Validators.min(0.00000001)]],
      remark: ['', Validators.maxLength(140)],
      mpin: ['', [Validators.required,Validators.pattern(/^[0-9]{6}$/)]],
    });
    this.withdrawalForm=fb.group({fromAccountId:['',Validators.required],amount:[null as number|null,[Validators.required,Validators.min(0.00000001)]],remark:['',Validators.maxLength(140)],mpin:['',[Validators.required,Validators.pattern(/^[0-9]{6}$/)]]});
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
  }
  statusLabel(status: string): string {
    return status.toLowerCase().split('_').map((part) => part ? part[0].toUpperCase() + part.slice(1) : '').join(' ');
  }
  load(): void {
    this.loading = true;
    this.fd.listSavings().subscribe({
      next: accounts => {
        this.accounts = accounts; this.loading = false;
        const active = accounts.filter(account => account.status === 'ACTIVE');
        this.transferOptions = active.map(account => ({ label: `${account.display_name} (••••${account.account_number.slice(-4)})`, value: String(account.account_id) }));
        this.withdrawalOptions=this.transferOptions;
        if (!active.some(account => account.account_id === Number(this.transferForm.controls.fromAccountId.value)))
          this.transferForm.controls.fromAccountId.setValue(active[0] ? String(active[0].account_id) : '');
      },
      error: (e: HttpErrorResponse) => { this.error = normalizeApiError(e).message; this.loading = false; },
    });
  }
  transfer(): void {
    if (this.transferBusy) return;
    if (this.transferForm.invalid) { this.transferForm.markAllAsTouched(); return; }
    const raw = this.transferForm.getRawValue();
    const fromAccountId = Number(raw.fromAccountId);
    const toAccountNumber = (raw.toAccountNumber ?? '').trim();
    const amount = Number(raw.amount);
    const remark = (raw.remark ?? '').trim();
    const signature = `${fromAccountId}|${toAccountNumber}|${String(amount)}|${remark}`;
    if (this.transferSubmission?.signature !== signature) this.transferSubmission = { signature, key: crypto.randomUUID() };
    const key = this.transferSubmission.key;
    this.transferBusy = true; this.transferError = ''; this.transferMessage = '';
    const pin=raw.mpin??'';this.transferBusy=true;
    this.mpin.requestProof('TRANSFER',{fromAccountId,toAccountNumber,amount},pin).subscribe({next:proof=>this.fd.transferSavings({ fromAccountId, toAccountNumber, amount, ...(remark ? { remark } : {}) }, key,proof.proofToken).subscribe({
      next: (result: SavingsTransferResponse) => {
        this.transferBusy = false; this.transferSubmission = null; this.transferReference = result.reference;this.clearTransferPin();
        this.transferMessage = 'Transfer completed. Reference: {0}';
        this.load(); this.loadHistory(fromAccountId);
      },
      error: error => { this.transferBusy = false; this.readMpinError('operation',error);this.clearTransferPin(); },
    }),error:error=>{this.transferBusy=false;this.readMpinError('verify',error);this.clearTransferPin();}});
  }
  withdraw():void{if(this.withdrawalBusy)return;if(this.withdrawalForm.invalid){this.withdrawalForm.markAllAsTouched();return;}const r=this.withdrawalForm.getRawValue(),account=Number(r.fromAccountId),amount=Number(r.amount),remark=(r.remark??'').trim(),signature=`${account}|${String(amount)}|${remark}`;if(this.withdrawalSubmission?.signature!==signature)this.withdrawalSubmission={signature,key:crypto.randomUUID()};this.withdrawalBusy=true;this.withdrawalError='';this.withdrawalMessage='';this.mpin.requestProof('WITHDRAWAL',{accountId:account,amount},r.mpin??'').subscribe({next:p=>this.fd.withdrawSavings({fromAccountId:account,amount,...(remark?{remark}:{})},this.withdrawalSubmission!.key,p.proofToken).subscribe({next:(x:SavingsWithdrawalResponse)=>{this.withdrawalBusy=false;this.withdrawalSubmission=null;this.withdrawalReference=x.reference;this.withdrawalMessage='Withdrawal completed. Reference: {0}';this.withdrawalForm.controls.mpin.setValue('');this.load();this.loadHistory(account);},error:e=>{this.withdrawalBusy=false;this.withdrawalForm.controls.mpin.setValue('');this.withdrawalError=classifyMpinError('operation',e);}}),error:e=>{this.withdrawalBusy=false;this.withdrawalForm.controls.mpin.setValue('');this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET';this.withdrawalError=classifyMpinError('verify',e);}});}
  private clearTransferPin():void{this.transferForm.controls.mpin.setValue('');}
  private readMpinError(stage:'verify'|'operation',error:any):void {
    this.mpinNotSet=stage==='verify'&&error?.error?.code==='MPIN_NOT_SET';
    this.transferError=classifyMpinError(stage,error);
  }
  private loadHistory(accountId: number): void {
    this.historyAccountId = accountId;
    this.fd.savingsTransactions(accountId).subscribe({ next: rows => this.transferHistory = rows, error: () => this.transferHistory = [] });
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
