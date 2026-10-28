import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { CurrentAccount, DepositProductOffer } from '../../core/models/fd.models';
import { DepositStatementPage, FdService, normalizeApiError, SavingsHistory } from '../../core/services/fd.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { GlassSelect } from '../../shared/components/glass-select/glass-select';

@Component({
  selector: 'app-current-accounts', standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink, TranslatePipe, WorkspaceShell, GlassSelect],
  template: `
    <app-workspace-shell>
      <main class="current-account-page">
        <header class="current-account-page__hero">
          <div class="current-account-page__hero-copy">
            <p class="current-account-page__eyebrow"><span class="current-account-page__eyebrow-dot" aria-hidden="true"></span>{{ 'Deposit accounts' | translate }}</p>
            <h1>{{ 'Current accounts' | translate }}<span class="current-account-page__gold">.</span></h1>
            <p class="current-account-page__intro">{{ 'Choose a published current account product and review its minimum balance and interest bands.' | translate }}</p>
            <a class="glass-btn glass-btn--ghost current-account-page__browse" routerLink="/current-accounts/accounts">
              {{ 'My current accounts' | translate }} <span aria-hidden="true">↗</span>
            </a>
          </div>
          <div class="current-account-page__hero-art" aria-hidden="true">
            <div class="current-account-page__hero-orbit"></div>
            <div class="current-account-page__hero-orbit current-account-page__hero-orbit--inner"></div>
            <div class="current-account-page__hero-symbol">↔</div>
            <span class="current-account-page__hero-spark current-account-page__hero-spark--one"></span>
            <span class="current-account-page__hero-spark current-account-page__hero-spark--two"></span>
          </div>
        </header>

        <div class="current-account-page__notices" aria-live="polite">
          <p class="glass-error" *ngIf="error" role="alert">{{ error | translate }}</p>
        </div>

        <section *ngIf="mode==='catalog'" class="current-account-page__catalog" aria-label="Current account products">
          <div class="current-account-page__product-grid">
            <article class="current-account-page__product" *ngFor="let item of offers; let i = index" [style.--account-index]="i">
              <div class="current-account-page__product-heading">
                <div>
                  <p class="current-account-page__eyebrow">{{ item.productCode }}</p>
                  <h2>{{ item.productName }}</h2>
                </div>
                <span class="current-account-page__product-mark" aria-hidden="true">↔</span>
              </div>
              <p class="current-account-page__description">{{ item.description }}</p>
              <div class="current-account-page__product-facts">
                <div><span>{{ 'Currencies' | translate }}</span><strong><span *ngFor="let c of item.currencies">{{ c.code }} </span></strong></div>
                <div><span>{{ 'Minimum balance' | translate }}</span><strong>{{ item.minimumBalance | number:'1.2-4' }}</strong></div>
              </div>
              <div class="current-account-page__rates" *ngIf="item.interestBands.length">
                <p class="current-account-page__eyebrow">{{ 'Interest rates' | translate }}</p>
                <div class="current-account-page__rate" *ngFor="let band of item.interestBands">
                  <span>{{ band.currencyCode }} · {{ band.valueFrom }}–{{ band.valueTo }}</span>
                  <strong>{{ band.rate | percent:'1.2-4' }}</strong>
                </div>
              </div>
              <a class="glass-btn current-account-page__open" [routerLink]="['/current-accounts/open',item.productId]">{{ 'Open account' | translate }}<span aria-hidden="true">→</span></a>
            </article>
          </div>
          <div class="current-account-page__empty" *ngIf="!loading&&!offers.length">
            <div class="current-account-page__empty-icon" aria-hidden="true">↔</div>
            <h2>{{ 'No current account products are available right now.' | translate }}</h2>
          </div>
        </section>

        <section *ngIf="mode==='open'&&offer" class="current-account-page__panel" aria-labelledby="open-current-account-heading">
          <a class="current-account-page__back" routerLink="/current-accounts">{{ 'Back to current account products' | translate }}<span aria-hidden="true">←</span></a>
          <div class="current-account-page__section-heading">
            <div class="current-account-page__section-icon" aria-hidden="true">＋</div>
            <div>
              <p class="current-account-page__eyebrow">{{ offer.productCode }}</p>
              <h2 id="open-current-account-heading">{{ offer.productName }}</h2>
            </div>
          </div>
          <p class="current-account-page__description">{{ offer.description }}</p>
          <div class="current-account-page__product-facts current-account-page__product-facts--open">
            <div><span>{{ 'Currencies' | translate }}</span><strong><span *ngFor="let c of offer.currencies">{{ c.code }} </span></strong></div>
            <div><span>{{ 'Minimum balance' | translate }}</span><strong>{{ offer.minimumBalance | number:'1.2-4' }}</strong></div>
          </div>
          <div class="current-account-page__rates" *ngIf="offer.interestBands.length">
            <p class="current-account-page__eyebrow">{{ 'Interest rates' | translate }}</p>
            <div class="current-account-page__rate" *ngFor="let band of offer.interestBands">
              <span>{{ band.currencyCode }} · {{ band.valueFrom }}–{{ band.valueTo }}</span>
              <strong>{{ band.rate | percent:'1.2-4' }}</strong>
            </div>
          </div>
          <form [formGroup]="form" (ngSubmit)="open()" class="current-account-page__form">
            <label class="glass-field">
              <span class="glass-field__label">{{ 'Currency' | translate }}</span>
              <app-glass-select formControlName="currencyCode" [options]="currencyOptions" />
            </label>
            <label class="glass-field">
              <span class="glass-field__label">{{ 'Account name' | translate }}</span>
              <input formControlName="displayName" maxlength="80">
              <span class="glass-field__error" *ngIf="form.controls.displayName.touched&&form.controls.displayName.invalid">{{ 'Enter a name of up to 80 characters.' | translate }}</span>
            </label>
            <button class="glass-btn current-account-page__submit" [disabled]="busy||form.invalid">{{ (busy?'Creating…':'Confirm and open') | translate }}<span *ngIf="!busy" aria-hidden="true">→</span></button>
          </form>
          <p class="glass-error" *ngIf="error.includes('KYC')">{{ error | translate }} <a routerLink="/kyc">{{ 'Go to KYC' | translate }}</a></p>
        </section>

        <section *ngIf="mode==='accounts'" class="current-account-page__accounts" aria-labelledby="current-accounts-heading">
          <div class="current-account-page__accounts-heading">
            <div>
              <p class="current-account-page__eyebrow">{{ 'Accounts' | translate }}</p>
              <h2 id="current-accounts-heading">{{ 'My current accounts' | translate }}</h2>
            </div>
            <span class="current-account-page__count" *ngIf="!loading">{{ '{0} shown' | translate: [(accounts.length)] }}</span>
          </div>
          <div class="current-account-page__loading" *ngIf="loading" role="status">
            <span class="current-account-page__loader"></span>{{ 'Loading accounts…' | translate }}
          </div>
          <div class="current-account-page__empty" *ngIf="!loading&&!error&&accounts.length===0">
            <div class="current-account-page__empty-icon" aria-hidden="true">↔</div>
            <h2>{{ 'No current accounts yet.' | translate }}</h2>
          </div>
          <article class="current-account-page__account" *ngFor="let account of accounts; let i = index" [style.--account-index]="i">
            <div class="current-account-page__account-top">
              <div class="current-account-page__account-icon" aria-hidden="true">↔</div>
              <div class="current-account-page__account-identity">
                <h3>{{ account.display_name }}</h3>
                <p>{{ account.productName }} · {{ account.currency_code }}</p>
              </div>
              <span class="current-account-page__status">{{ account.status | translate }}</span>
            </div>
            <div class="current-account-page__balance">
              <span>{{ 'Balance' | translate }}</span>
              <strong>{{ account.balance | number:'1.2-4' }} <small>{{ account.currency_code }}</small></strong>
            </div>
            <div class="current-account-page__account-facts">
              <div><span>{{ 'Available balance' | translate }}</span><strong>{{ account.availableBalance ?? account.balance | number:'1.2-4' }}</strong></div>
              <div><span>{{ 'Minimum balance' | translate }}</span><strong>{{ account.minimumBalance | number:'1.2-4' }}</strong></div>
            </div>
            <p class="current-account-page__inactive" *ngIf="account.status==='INACTIVE'">{{ 'This account is frozen. Customer deposits and withdrawals are disabled.' | translate }}</p>
            <a class="current-account-page__deposit-link" routerLink="/savings">{{ 'Transfer or withdraw using the deposit tools' | translate }}<span aria-hidden="true">↗</span></a>
            <div class="statement-filters">
              <label class="glass-field"><span class="glass-field__label">{{ 'From date' | translate }}</span><input type="date" [(ngModel)]="fromDate"></label>
              <label class="glass-field"><span class="glass-field__label">{{ 'To date' | translate }}</span><input type="date" [(ngModel)]="toDate"></label>
              <button class="glass-btn glass-btn--ghost" (click)="loadStatement(account,0)">{{ 'View statement' | translate }}</button>
              <button class="glass-btn glass-btn--ghost" (click)="download(account)">{{ 'Download CSV statement' | translate }}</button>
              <button class="glass-btn glass-btn--ghost" (click)="loadHistory(account)">{{ 'Transaction history' | translate }}</button>
            </div>
            <section class="statement" *ngIf="statementAccountId===account.account_id&&statement" aria-label="Current account statement">
              <div class="current-account-page__statement-summary">
                <p>{{ 'Opening balance' | translate }}<strong>{{ statement.openingBalance | number:'1.2-4' }} {{ account.currency_code }}</strong></p>
                <p>{{ 'Closing balance' | translate }}<strong>{{ statement.closingBalance | number:'1.2-4' }} {{ account.currency_code }}</strong></p>
              </div>
              <div class="statement-row" *ngFor="let row of statement.content"><span>{{ row.postedAt | date:'medium' }} · {{ row.reference }}</span><strong>{{ row.entryType==='CREDIT'?'+':'−' }}{{ row.amount | number:'1.2-4' }} {{ account.currency_code }}</strong></div>
              <nav class="statement-pages" aria-label="Statement pages">
                <button class="glass-btn glass-btn--ghost" (click)="loadStatement(account,statement.page-1)" [disabled]="statement.page===0">{{ 'Previous' | translate }}</button>
                <span>{{ 'Page {0} of {1}' | translate:[statement.page+1,statement.totalPages] }}</span>
                <button class="glass-btn glass-btn--ghost" (click)="loadStatement(account,statement.page+1)" [disabled]="statement.page+1>=statement.totalPages">{{ 'Next' | translate }}</button>
              </nav>
            </section>
            <section class="statement" *ngIf="historyAccountId===account.account_id" aria-label="Current account transaction history">
              <div class="statement-row" *ngFor="let tx of history"><span>{{ tx.st_posted_at | date:'medium' }} · {{ tx.st_reference_id }}</span><strong>{{ tx.st_entry_type==='CREDIT'?'+':'−' }}{{ tx.st_amount | number:'1.2-4' }} {{ account.currency_code }}</strong></div>
            </section>
          </article>
        </section>
      </main>
    </app-workspace-shell>`,
  styleUrl: './current-accounts.scss',
})
export class CurrentAccountsPage implements OnInit {
  mode: 'catalog'|'open'|'accounts' = 'catalog';
  offers: DepositProductOffer[] = []; offer: DepositProductOffer|null = null; accounts: CurrentAccount[] = [];
  currencyOptions: Array<{label:string;value:string}> = []; loading=false; busy=false; error='';
  statement: DepositStatementPage|null = null; statementAccountId:number|null=null; history: SavingsHistory[]=[]; historyAccountId:number|null=null;
  fromDate=''; toDate=''; private key:string|null=null; private signature=''; readonly form;
  constructor(private readonly fd:FdService,private readonly route:ActivatedRoute,fb:FormBuilder) {
    this.form = fb.nonNullable.group({
      currencyCode: ['', Validators.required],
      displayName: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/.*\S.*/)]],
    });
  }
  ngOnInit():void {
    const productId=Number(this.route.snapshot.paramMap.get('productId'));
    this.mode=productId?'open':this.route.snapshot.url.some(s=>s.path==='accounts')?'accounts':'catalog';
    if(this.mode==='open')this.fd.getCasaOffer('CURRENT',productId).subscribe({next:o=>{this.offer=o;this.currencyOptions=o.currencies.map(c=>({label:c.code,value:c.code}));this.form.controls.currencyCode.setValue(o.currencies[0]?.code??'');this.fd.listCurrentAccounts().subscribe(a=>this.form.controls.displayName.setValue(`Current Acc ${a.length+1}`));},error:e=>this.error=normalizeApiError(e).message});
    else if(this.mode==='accounts')this.loadAccounts();
    else this.fd.listCasaOffers('CURRENT').subscribe({next:x=>this.offers=x,error:e=>this.error=normalizeApiError(e).message});
  }
  open():void { if(this.busy||!this.offer)return;if(this.form.invalid){this.form.markAllAsTouched();return;}const v=this.form.getRawValue(),sig=`${this.offer.productId}|${v.currencyCode}|${v.displayName.trim()}`;if(sig!==this.signature){this.signature=sig;this.key=crypto.randomUUID();}this.busy=true;this.error='';this.fd.createCurrentAccount({productId:this.offer.productId,currencyCode:v.currencyCode,displayName:v.displayName.trim()},this.key!).subscribe({next:()=>{this.busy=false;location.assign('/current-accounts/accounts');},error:(e:HttpErrorResponse)=>{this.busy=false;this.error=normalizeApiError(e).message;}}); }
  loadStatement(account:CurrentAccount,page:number):void { this.statementAccountId=account.account_id;this.fd.getCurrentStatement(account.account_id,this.fromDate||undefined,this.toDate||undefined,page,25).subscribe({next:data=>this.statement=data,error:e=>this.error=normalizeApiError(e).message}); }
  loadHistory(account:CurrentAccount):void { this.historyAccountId=account.account_id;this.fd.currentTransactions(account.account_id).subscribe({next:rows=>this.history=rows,error:e=>this.error=normalizeApiError(e).message}); }
  download(account:CurrentAccount):void { this.fd.downloadCurrentStatement(account.account_id,this.fromDate||undefined,this.toDate||undefined).subscribe({next:blob=>{const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`current-account-${account.account_id}.csv`;a.click();URL.revokeObjectURL(url);},error:e=>this.error=normalizeApiError(e).message}); }
  private loadAccounts():void { this.loading=true;this.fd.listCurrentAccounts().subscribe({next:a=>{this.accounts=a;this.loading=false;},error:e=>{this.loading=false;this.error=normalizeApiError(e).message;}}); }
}
