import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  FdComplianceService,
  ComplianceClock,
  ComplianceDateResult,
  ComplianceRateResult,
  ProductSchedule,
  UpdateScheduleRequest
} from '../../core/services/fd-compliance.service';
import { FdProductAdminService, FdOfferAdmin } from '../../core/services/fd-product-admin.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';

interface EditableRateBand {
  ruleId: number;
  currencyCode: string;
  minimumTenureMonths: number;
  maximumTenureMonths: number;
  ratePercent: number;
  compoundingBasis: string;
  payoutFrequency: string;
  effectiveFrom: string;
  effectiveTo: string;
}

@Component({
  selector: 'app-fd-compliance', standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  template: `
    <app-workspace-shell>
      <main class="glass-card glass-card--wide compliance-card">
        <a routerLink="/dashboard" class="glass-link"> {{ '← Staff overview' | translate }} </a>
        <header class="compliance-head">
          <div>
            <p class="fd-eyebrow"> {{ 'Controls & assurance' | translate }} </p>
            <h2> {{ 'Fixed-deposit compliance' | translate }} </h2>
            <p class="glass-card__sub"> {{ 'Check customer-facing rate disclosures, verify rate schedules against effective-date windows, and manage authoritative product rate schedules.' | translate }} </p>
          </div>
          <span class="fd-admin-badge"> {{ 'STAFF ONLY' | translate }} </span>
        </header>

        <p class="glass-error" *ngIf="error" role="alert">{{ error | translate }}</p>
        <p class="compliance-note" *ngIf="clock"> {{ 'Evaluation dates use {0} business time. System date: {1}.' | translate: [(clock.business_zone), (clock.system_date | date:'longDate')] }} </p>

        <div class="compliance-grid">
          <!-- 01 · Disclosure check -->
          <section class="check-panel">
            <p class="fd-eyebrow"> {{ '01 · Disclosure check' | translate }} </p>
            <h3> {{ 'Compare disclosed rate' | translate }} </h3>
            <p> {{ 'Compare the rate shown to a customer with the authoritative active rate schedule.' | translate }} </p>
            <form (ngSubmit)="verify()">
              <ng-container *ngTemplateOutlet="commonFields"></ng-container>
              <label class="glass-field">
                <span class="glass-field__label"> {{ 'Disclosed annual rate (%)' | translate }} </span>
                <input type="number" name="disclosedRate" [(ngModel)]="disclosedRate" min="0" max="100" step="0.01" required />
              </label>
              <button class="glass-btn" type="submit" [disabled]="busy || !validInputs">
                {{ (busy === 'rate' ? 'Checking…' : 'Verify disclosure') | translate }}
              </button>
            </form>
            <article class="result" *ngIf="rateResult as result" [class.result--good]="result.isMatch" [class.result--bad]="!result.isMatch">
              <div class="result__headline">
                <strong>{{ result.matchStatus.replaceAll('_',' ') }}</strong>
                <span>{{ result.evaluationDate | date:'mediumDate' }}</span>
              </div>
              <p>{{ result.explanation }}</p>
              <dl>
                <div><dt> {{ 'Disclosed' | translate }} </dt><dd>{{ result.displayedRate | percent:'1.2-4' }}</dd></div>
                <div><dt> {{ 'Authoritative' | translate }} </dt><dd> {{ (result.authoritativeRate === null ? '—' : (result.authoritativeRate | percent:'1.2-4')) | translate }} </dd></div>
                <div *ngIf="result.compoundingFrequency"><dt> {{ 'Compounding' | translate }} </dt><dd>{{ result.compoundingFrequency }}</dd></div>
              </dl>
            </article>
          </section>

          <!-- 02 · Effective-date check -->
          <section class="check-panel">
            <p class="fd-eyebrow"> {{ '02 · Effective-date check' | translate }} </p>
            <h3> {{ 'Inspect schedule validity' | translate }} </h3>
            <p> {{ 'Confirm the selected product, amount, and tenure have one valid schedule on the selected date.' | translate }} </p>
            <form (ngSubmit)="evaluate()">
              <ng-container *ngTemplateOutlet="commonFields"></ng-container>
              <label class="glass-field">
                <span class="glass-field__label"> {{ 'Evaluation date' | translate }} </span>
                <input type="date" name="evaluationDate" [(ngModel)]="evaluationDate" required />
              </label>
              <button class="glass-btn" type="submit" [disabled]="busy || !validInputs">
                {{ (busy === 'date' ? 'Evaluating…' : 'Evaluate date') | translate }}
              </button>
            </form>
            <article class="result" *ngIf="dateResult as result" [class.result--good]="result.isValid" [class.result--bad]="!result.isValid">
              <div class="result__headline">
                <strong>{{ result.validityStatus.replaceAll('_',' ') }}</strong>
                <span>{{ result.evaluationDate | date:'mediumDate' }}</span>
              </div>
              <p>{{ result.explanation }}</p>
              <dl>
                <div><dt> {{ 'Applicable rate' | translate }} </dt><dd> {{ (result.applicableRate === null ? '—' : (result.applicableRate | percent:'1.2-4')) | translate }} </dd></div>
                <div><dt> {{ 'Effective from' | translate }} </dt><dd>{{ result.effectiveFrom ? (result.effectiveFrom | date:'mediumDate') : 'Open' }}</dd></div>
                <div><dt> {{ 'Effective to' | translate }} </dt><dd>{{ result.effectiveTo ? (result.effectiveTo | date:'mediumDate') : 'Open' }}</dd></div>
              </dl>
            </article>
          </section>
        </div>

        <!-- 03 · Authoritative Rate Schedule Management -->
        <section class="schedule-panel">
          <header class="schedule-panel__head">
            <div>
              <p class="fd-eyebrow">{{ '03 · Authoritative Rate Schedule Management' | translate }}</p>
              <h3>{{ 'Manage & Publish FD Rate Schedules' | translate }}</h3>
              <p class="schedule-panel__sub">
                {{ 'Inspect, configure, and safely publish interest rate schedules directly into the authoritative product catalog.' | translate }}
              </p>
            </div>
            <div class="schedule-product-select">
              <label class="glass-field">
                <span class="glass-field__label">{{ 'Select Fixed-Deposit Product' | translate }}</span>
                <select [ngModel]="selectedManageProductId" (ngModelChange)="onSelectManageProduct($event)" [disabled]="scheduleLoading || scheduleSaving">
                  <option [ngValue]="null">{{ 'Choose a product to inspect…' | translate }}</option>
                  <option *ngFor="let p of allProducts" [ngValue]="p.catalog_product_id">
                    {{ p.product_name }} ({{ p.product_code }}) — {{ p.status }}
                  </option>
                </select>
              </label>
            </div>
          </header>

          <!-- Banners -->
          <div class="glass-error schedule-banner" *ngIf="scheduleError" role="alert">
            {{ scheduleError | translate }}
          </div>
          <div class="schedule-success schedule-banner" *ngIf="scheduleSuccess" role="status">
            {{ scheduleSuccess | translate }}
          </div>

          <!-- Loading State -->
          <div class="schedule-loading" *ngIf="scheduleLoading">
            <span class="fd-loader-spinner"></span>
            <p>{{ 'Loading authoritative schedule from product catalog…' | translate }}</p>
          </div>

          <!-- Empty Prompt -->
          <div class="schedule-empty" *ngIf="!scheduleLoading && !schedule">
            <p>{{ 'Select an FD product above to inspect its authoritative rate bands, effective dates, and publication status.' | translate }}</p>
          </div>

          <!-- Loaded Schedule View / Edit -->
          <div class="schedule-content" *ngIf="!scheduleLoading && schedule as s">
            <!-- Product Header Summary -->
            <div class="schedule-meta-bar">
              <div class="meta-item">
                <span class="meta-label">{{ 'Product Code' | translate }}</span>
                <strong>{{ s.productCode }}</strong>
              </div>
              <div class="meta-item">
                <span class="meta-label">{{ 'Product Name' | translate }}</span>
                <strong>{{ s.productName }}</strong>
              </div>
              <div class="meta-item">
                <span class="meta-label">{{ 'Lifecycle Status' | translate }}</span>
                <span class="status-badge" [ngClass]="'status--' + s.status.toLowerCase()">{{ s.status }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">{{ 'Effective Window' | translate }}</span>
                <span>{{ (s.effectiveFrom ? (s.effectiveFrom | date:'mediumDate') : 'Any') }} → {{ (s.effectiveTo ? (s.effectiveTo | date:'mediumDate') : 'Open') }}</span>
              </div>
              <div class="meta-actions" *ngIf="!isEditingSchedule">
                <button type="button" class="glass-btn glass-btn--primary" (click)="startEditing()">
                  {{ '✎ Edit Rate Schedule' | translate }}
                </button>
              </div>
            </div>

            <!-- VIEW MODE TABLE -->
            <div *ngIf="!isEditingSchedule" class="table-container">
              <table class="schedule-table" aria-label="Authoritative Rate Bands">
                <thead>
                  <tr>
                    <th>{{ 'Currency' | translate }}</th>
                    <th>{{ 'Tenure Range' | translate }}</th>
                    <th>{{ 'Annual Rate' | translate }}</th>
                    <th>{{ 'Compounding' | translate }}</th>
                    <th>{{ 'Payout' | translate }}</th>
                    <th>{{ 'Effective From' | translate }}</th>
                    <th>{{ 'Effective To' | translate }}</th>
                    <th>{{ 'Band Status' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let band of s.rateBands">
                    <td><strong class="currency-tag">{{ band.currencyCode }}</strong></td>
                    <td>{{ band.minimumTenureMonths }} – {{ band.maximumTenureMonths }} mo</td>
                    <td><strong class="rate-highlight">{{ (band.annualRate * 100) | number:'1.2-2' }}% p.a.</strong></td>
                    <td>{{ band.compoundingBasis }}</td>
                    <td>{{ band.payoutFrequency }}</td>
                    <td>{{ band.effectiveFrom ? (band.effectiveFrom | date:'mediumDate') : 'Open' }}</td>
                    <td>{{ band.effectiveTo ? (band.effectiveTo | date:'mediumDate') : 'Open' }}</td>
                    <td>
                      <span class="status-badge" [ngClass]="'status--' + band.status.toLowerCase()">
                        {{ band.status }}
                      </span>
                    </td>
                  </tr>
                  <tr *ngIf="s.rateBands.length === 0">
                    <td colspan="8" class="empty-table-msg">
                      {{ 'No active rate bands configured for this product.' | translate }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- EDIT MODE FORM -->
            <div *ngIf="isEditingSchedule" class="schedule-edit-container">
              <div class="edit-section-header">
                <h4>{{ 'Authoring Rate Bands & Effective Dates' | translate }}</h4>
                <p class="edit-hint">
                  {{ 'Update tenure limits, annual interest rates, and compounding rules. Submitting will validate the schedule with FdComplianceService before safely updating and publishing to product.catalog.' | translate }}
                </p>
              </div>

              <!-- Product Effective Date Inputs -->
              <div class="global-dates-bar">
                <label class="glass-field">
                  <span class="glass-field__label">{{ 'Product Effective From' | translate }}</span>
                  <input type="date" [(ngModel)]="editableProductEffectiveFrom" />
                </label>
                <label class="glass-field">
                  <span class="glass-field__label">{{ 'Product Effective To' | translate }}</span>
                  <input type="date" [(ngModel)]="editableProductEffectiveTo" />
                </label>
              </div>

              <!-- Bands Edit List -->
              <div class="bands-edit-list">
                <div class="band-edit-row" *ngFor="let band of editableBands; let i = index">
                  <div class="band-field-group">
                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'Currency' | translate }}</span>
                      <select [(ngModel)]="band.currencyCode" required>
                        <option *ngFor="let c of s.currencies" [value]="c.currencyCode">{{ c.currencyCode }}</option>
                      </select>
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'Min Mo' | translate }}</span>
                      <input type="number" [(ngModel)]="band.minimumTenureMonths" min="1" max="1200" required />
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'Max Mo' | translate }}</span>
                      <input type="number" [(ngModel)]="band.maximumTenureMonths" min="1" max="1200" required />
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'Rate (% p.a.)' | translate }}</span>
                      <input type="number" [(ngModel)]="band.ratePercent" min="0.01" max="100" step="0.01" required />
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'Compounding' | translate }}</span>
                      <select [(ngModel)]="band.compoundingBasis" required>
                        <option value="QUARTERLY">QUARTERLY</option>
                        <option value="MONTHLY">MONTHLY</option>
                        <option value="HALF_YEARLY">HALF_YEARLY</option>
                        <option value="ANNUALLY">ANNUALLY</option>
                        <option value="SIMPLE">SIMPLE</option>
                      </select>
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'Payout' | translate }}</span>
                      <select [(ngModel)]="band.payoutFrequency" required>
                        <option value="AT_MATURITY">AT_MATURITY</option>
                        <option value="MONTHLY">MONTHLY</option>
                        <option value="QUARTERLY">QUARTERLY</option>
                      </select>
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'From' | translate }}</span>
                      <input type="date" [(ngModel)]="band.effectiveFrom" />
                    </label>

                    <label class="glass-field">
                      <span class="glass-field__label">{{ 'To' | translate }}</span>
                      <input type="date" [(ngModel)]="band.effectiveTo" />
                    </label>
                  </div>

                  <button type="button" class="remove-band-btn" (click)="removeBand(i)" [disabled]="editableBands.length <= 1" title="Remove band">
                    ✕
                  </button>
                </div>
              </div>

              <!-- Controls -->
              <div class="edit-actions-bar">
                <button type="button" class="glass-btn glass-btn--ghost" (click)="addBand()">
                  {{ '+ Add Rate Band' | translate }}
                </button>
                <div class="primary-actions">
                  <button type="button" class="glass-btn glass-btn--ghost" (click)="cancelEditing()" [disabled]="scheduleSaving">
                    {{ 'Cancel' | translate }}
                  </button>
                  <button type="button" class="glass-btn glass-btn--primary" (click)="saveSchedule()" [disabled]="scheduleSaving">
                    {{ (scheduleSaving ? 'Validating & Publishing…' : 'Save & Publish Changes') | translate }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Common fields template for panels 01 & 02 -->
        <ng-template #commonFields>
          <label class="glass-field">
            <span class="glass-field__label"> {{ 'Fixed-deposit product' | translate }} </span>
            <select name="product" [(ngModel)]="productId" required>
              <option [ngValue]="null"> {{ 'Select product' | translate }} </option>
              <option *ngFor="let product of products" [ngValue]="product.catalog_product_id">
                {{ product.product_name }} · {{ product.product_code }}
              </option>
            </select>
          </label>
          <div class="field-pair">
            <label class="glass-field">
              <span class="glass-field__label"> {{ 'Currency' | translate }} </span>
              <select name="currency" [(ngModel)]="currency" required>
                <option *ngFor="let code of currencies" [value]="code">{{ code }}</option>
              </select>
            </label>
            <label class="glass-field">
              <span class="glass-field__label"> {{ 'Tenure (months)' | translate }} </span>
              <input type="number" name="tenure" [(ngModel)]="tenure" min="1" max="1200" step="1" required />
            </label>
          </div>
          <label class="glass-field">
            <span class="glass-field__label"> {{ 'Principal amount' | translate }} </span>
            <input type="number" name="principal" [(ngModel)]="principal" min="0.01" step="0.01" required />
          </label>
        </ng-template>
      </main>
    </app-workspace-shell>
  `,
  styles: [`
    .compliance-card { box-sizing: border-box; width: 100%; max-width: 1180px; margin: 24px; }
    .compliance-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 18px; margin: 20px 0; }
    .compliance-head h2 { margin: 0; font: 600 var(--fs-xl) var(--font-display); }
    .compliance-note { padding: 12px 16px; border-left: 2px solid var(--gold-500); color: var(--ink-300); background: rgba(214,174,88,.08); border-radius: 4px; }
    .compliance-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 18px; align-items: start; }
    .check-panel { padding: 22px; border: 1px solid var(--ws-line); border-radius: 14px; background: var(--ws-surface-raised); }
    .check-panel h3 { margin: 6px 0; font: 600 1.25rem var(--font-display); }
    .check-panel > p:not(.fd-eyebrow) { color: var(--ink-400); line-height: 1.5; }
    .check-panel form { display: grid; gap: 14px; margin-top: 20px; }
    .field-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .result { margin-top: 22px; padding: 18px; border: 1px solid var(--ws-line); border-radius: 12px; background: rgba(255,255,255,.035); }
    .result--good { border-color: rgba(126,209,151,.45); }
    .result--bad { border-color: rgba(235,140,128,.45); }
    .result__headline { display: flex; justify-content: space-between; gap: 8px; color: white; }
    .result__headline strong { color: var(--gold-500); font: .8rem var(--font-mono); }
    .result p { color: var(--ink-300); line-height: 1.55; }
    .result dl { display: flex; gap: 22px; flex-wrap: wrap; margin-top: 12px; }
    .result dl div { display: grid; gap: 4px; }
    .result dt { font-size: .72rem; color: var(--ink-400); }
    .result dd { margin: 0; color: white; }

    /* Schedule Panel (03) */
    .schedule-panel { margin-top: 28px; padding: 24px; border: 1px solid var(--ws-line); border-radius: 14px; background: var(--ws-surface-raised); }
    .schedule-panel__head { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; flex-wrap: wrap; margin-bottom: 20px; }
    .schedule-panel__head h3 { margin: 6px 0; font: 600 1.35rem var(--font-display); }
    .schedule-panel__sub { color: var(--ink-400); line-height: 1.5; margin: 0; max-width: 620px; }
    .schedule-product-select { min-width: 280px; }
    .schedule-banner { margin-bottom: 18px; padding: 12px 16px; border-radius: 8px; }
    .schedule-success { background: rgba(126,209,151,.12); border: 1px solid rgba(126,209,151,.4); color: #7ed197; font-weight: 500; }
    .schedule-loading, .schedule-empty { padding: 40px 20px; text-align: center; color: var(--ink-400); }
    .schedule-meta-bar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 18px; background: rgba(255,255,255,.03); border: 1px solid var(--ws-line); border-radius: 10px; margin-bottom: 18px; }
    .meta-item { display: grid; gap: 2px; }
    .meta-label { font-size: .72rem; color: var(--ink-400); text-transform: uppercase; letter-spacing: 0.04em; }
    .status-badge { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: .75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
    .status--active { background: rgba(126,209,151,.15); color: #7ed197; border: 1px solid rgba(126,209,151,.4); }
    .status--draft { background: rgba(214,174,88,.15); color: var(--gold-500); border: 1px solid rgba(214,174,88,.4); }
    .status--expired { background: rgba(235,140,128,.15); color: #eb8c80; border: 1px solid rgba(235,140,128,.4); }
    .status--future { background: rgba(138,180,248,.15); color: #8ab4f8; border: 1px solid rgba(138,180,248,.4); }
    .status--inactive { background: rgba(255,255,255,.08); color: var(--ink-400); border: 1px solid var(--ws-line); }

    /* Table */
    .table-container { overflow-x: auto; margin-top: 10px; }
    .schedule-table { width: 100%; border-collapse: collapse; font-size: .88rem; text-align: left; }
    .schedule-table th { padding: 12px 14px; color: var(--ink-400); font-size: .75rem; text-transform: uppercase; letter-spacing: .05em; border-bottom: 1px solid var(--ws-line); font-weight: 500; }
    .schedule-table td { padding: 14px; border-bottom: 1px solid rgba(255,255,255,.04); color: var(--ink-200); }
    .currency-tag { color: var(--gold-400); font-family: var(--font-mono); }
    .rate-highlight { color: #fff; font-size: .95rem; font-family: var(--font-mono); }
    .empty-table-msg { text-align: center; color: var(--ink-400); padding: 24px; }

    /* Edit Mode */
    .schedule-edit-container { display: grid; gap: 18px; margin-top: 10px; }
    .edit-section-header h4 { margin: 0 0 4px; font-size: 1.1rem; font-family: var(--font-display); }
    .edit-hint { margin: 0; color: var(--ink-400); font-size: .84rem; line-height: 1.45; }
    .global-dates-bar { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; max-width: 480px; }
    .bands-edit-list { display: grid; gap: 12px; }
    .band-edit-row { display: flex; align-items: flex-end; gap: 10px; padding: 12px; background: rgba(255,255,255,.02); border: 1px solid var(--ws-line); border-radius: 8px; flex-wrap: wrap; }
    .band-field-group { display: flex; flex-wrap: wrap; gap: 10px; flex: 1; }
    .band-field-group .glass-field { min-width: 90px; flex: 1; }
    .remove-band-btn { background: rgba(235,140,128,.1); border: 1px solid rgba(235,140,128,.3); color: #eb8c80; border-radius: 6px; width: 36px; height: 38px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 1rem; margin-bottom: 2px; }
    .remove-band-btn:disabled { opacity: 0.3; cursor: not-allowed; }
    .edit-actions-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-top: 10px; }
    .primary-actions { display: flex; gap: 10px; }

    @media(max-width: 780px) {
      .compliance-card { margin: 12px; }
      .compliance-grid { grid-template-columns: 1fr; }
      .compliance-head { flex-direction: column; }
      .global-dates-bar { grid-template-columns: 1fr; }
      .band-edit-row { flex-direction: column; align-items: stretch; }
      .remove-band-btn { align-self: flex-end; }
    }
    @media(max-width: 480px) {
      .field-pair { grid-template-columns: 1fr; }
    }
  `],
})
export class FdCompliance implements OnInit {
  allProducts: FdOfferAdmin[] = [];
  products: FdOfferAdmin[] = [];
  currencies: string[] = [];
  clock: ComplianceClock | null = null;

  // Verification states
  productId: number | null = null;
  currency = '';
  principal: number | null = null;
  tenure: number | null = null;
  disclosedRate: number | null = null;
  evaluationDate = new Date().toISOString().slice(0, 10);
  rateResult: ComplianceRateResult | null = null;
  dateResult: ComplianceDateResult | null = null;
  busy: '' | 'rate' | 'date' = '';
  error = '';

  // Rate Schedule Management states (Panel 03)
  selectedManageProductId: number | null = null;
  schedule: ProductSchedule | null = null;
  scheduleLoading = false;
  scheduleSaving = false;
  scheduleError = '';
  scheduleSuccess = '';
  isEditingSchedule = false;
  editableBands: EditableRateBand[] = [];
  editableProductEffectiveFrom = '';
  editableProductEffectiveTo = '';

  constructor(
    private readonly compliance: FdComplianceService,
    private readonly catalog: FdProductAdminService
  ) {}

  get validInputs(): boolean {
    return this.productId !== null && !!this.currency && this.principal !== null && this.principal > 0 && this.tenure !== null && this.tenure > 0;
  }

  ngOnInit(): void {
    this.catalog.listOffers().subscribe({
      next: offers => {
        this.allProducts = offers;
        this.products = offers.filter(item => item.status === 'ACTIVE');
        this.currencies = [...new Set(this.products.flatMap(item => item.currency_codes))].sort();
        this.currency = this.currencies[0] ?? '';
        if (this.allProducts.length > 0 && this.selectedManageProductId === null) {
          this.onSelectManageProduct(this.allProducts[0].catalog_product_id);
        }
      },
      error: e => this.error = this.errorText(e)
    });

    this.compliance.clock().subscribe({
      next: clock => {
        this.clock = clock;
        this.evaluationDate = clock.business_date;
      },
      error: e => this.error = this.errorText(e)
    });
  }

  verify(): void {
    if (!this.validInputs || this.productId === null || this.principal === null || this.tenure === null || this.disclosedRate === null) return;
    this.busy = 'rate';
    this.error = '';
    this.compliance.verifyRate({
      product_id: this.productId,
      currency_code: this.currency,
      principal_amount: this.principal,
      tenure_months: this.tenure,
      displayed_rate: this.disclosedRate / 100,
      evaluation_date: this.evaluationDate
    }).subscribe({
      next: r => { this.rateResult = r; this.busy = ''; },
      error: e => { this.error = this.errorText(e); this.busy = ''; }
    });
  }

  evaluate(): void {
    if (!this.validInputs || this.productId === null || this.principal === null || this.tenure === null) return;
    this.busy = 'date';
    this.error = '';
    this.compliance.evaluateDate({
      product_id: this.productId,
      currency_code: this.currency,
      principal_amount: this.principal,
      tenure_months: this.tenure,
      evaluation_date: this.evaluationDate
    }).subscribe({
      next: r => { this.dateResult = r; this.busy = ''; },
      error: e => { this.error = this.errorText(e); this.busy = ''; }
    });
  }

  onSelectManageProduct(id: number | null): void {
    this.selectedManageProductId = id;
    this.schedule = null;
    this.scheduleError = '';
    this.scheduleSuccess = '';
    this.isEditingSchedule = false;
    if (id === null) return;

    this.scheduleLoading = true;
    this.compliance.getSchedule(id).subscribe({
      next: s => {
        this.schedule = s;
        this.scheduleLoading = false;
      },
      error: e => {
        this.scheduleError = this.errorText(e);
        this.scheduleLoading = false;
      }
    });
  }

  startEditing(): void {
    if (!this.schedule) return;
    this.scheduleError = '';
    this.scheduleSuccess = '';
    this.editableProductEffectiveFrom = this.schedule.effectiveFrom ? this.schedule.effectiveFrom.slice(0, 10) : '';
    this.editableProductEffectiveTo = this.schedule.effectiveTo ? this.schedule.effectiveTo.slice(0, 10) : '';
    this.editableBands = this.schedule.rateBands.map(b => ({
      ruleId: b.ruleId,
      currencyCode: b.currencyCode,
      minimumTenureMonths: b.minimumTenureMonths,
      maximumTenureMonths: b.maximumTenureMonths,
      ratePercent: Number((b.annualRate * 100).toFixed(4)),
      compoundingBasis: b.compoundingBasis || 'QUARTERLY',
      payoutFrequency: b.payoutFrequency || 'AT_MATURITY',
      effectiveFrom: b.effectiveFrom ? b.effectiveFrom.slice(0, 10) : '',
      effectiveTo: b.effectiveTo ? b.effectiveTo.slice(0, 10) : '',
    }));
    if (this.editableBands.length === 0) {
      this.addBand();
    }
    this.isEditingSchedule = true;
  }

  cancelEditing(): void {
    this.isEditingSchedule = false;
    this.scheduleError = '';
  }

  addBand(): void {
    const defaultCurrency = this.schedule?.currencies[0]?.currencyCode || 'INR';
    this.editableBands.push({
      ruleId: 0,
      currencyCode: defaultCurrency,
      minimumTenureMonths: 12,
      maximumTenureMonths: 24,
      ratePercent: 7.00,
      compoundingBasis: 'QUARTERLY',
      payoutFrequency: 'AT_MATURITY',
      effectiveFrom: this.editableProductEffectiveFrom,
      effectiveTo: this.editableProductEffectiveTo,
    });
  }

  removeBand(index: number): void {
    if (this.editableBands.length > 1) {
      this.editableBands.splice(index, 1);
    }
  }

  saveSchedule(): void {
    if (!this.selectedManageProductId || !this.schedule) return;
    this.scheduleError = '';
    this.scheduleSuccess = '';

    // Client-side validations
    if (this.editableProductEffectiveFrom && this.editableProductEffectiveTo &&
        this.editableProductEffectiveFrom > this.editableProductEffectiveTo) {
      this.scheduleError = 'Product effective-from date cannot be after effective-to date.';
      return;
    }

    if (this.editableBands.length === 0) {
      this.scheduleError = 'Rate schedule requires at least one rate band.';
      return;
    }

    for (let i = 0; i < this.editableBands.length; i++) {
      const b = this.editableBands[i];
      if (b.ratePercent === null || b.ratePercent === undefined || b.ratePercent <= 0 || b.ratePercent > 100) {
        this.scheduleError = `Band ${i + 1}: Annual interest rate must be between 0.01% and 100%.`;
        return;
      }
      if (b.minimumTenureMonths < 1 || b.maximumTenureMonths < b.minimumTenureMonths) {
        this.scheduleError = `Band ${i + 1}: Minimum tenure must be >= 1 and <= maximum tenure.`;
        return;
      }
      if (b.effectiveFrom && b.effectiveTo && b.effectiveFrom > b.effectiveTo) {
        this.scheduleError = `Band ${i + 1}: Effective-from date cannot be after effective-to date.`;
        return;
      }
    }

    const payload: UpdateScheduleRequest = {
      effectiveFrom: this.editableProductEffectiveFrom || null,
      effectiveTo: this.editableProductEffectiveTo || null,
      rateBands: this.editableBands.map(b => ({
        currencyCode: b.currencyCode,
        minimumTenureMonths: b.minimumTenureMonths,
        maximumTenureMonths: b.maximumTenureMonths,
        annualRate: Number((b.ratePercent / 100).toFixed(6)),
        compoundingBasis: b.compoundingBasis,
        payoutFrequency: b.payoutFrequency,
        effectiveFrom: b.effectiveFrom || null,
        effectiveTo: b.effectiveTo || null,
      }))
    };

    this.scheduleSaving = true;
    this.compliance.updateSchedule(this.selectedManageProductId, payload).subscribe({
      next: updated => {
        this.schedule = updated;
        this.isEditingSchedule = false;
        this.scheduleSaving = false;
        this.scheduleSuccess = 'Rate schedule validated and published successfully to authoritative catalog.';
      },
      error: (e: HttpErrorResponse) => {
        this.scheduleSaving = false;
        this.scheduleError = this.errorText(e);
      }
    });
  }

  private errorText(e: HttpErrorResponse): string {
    if (e.status === 401) return 'Your session expired. Sign in again.';
    if (e.status === 403) return 'Rate management and compliance checks require an authorized staff account.';
    return e.error?.message ?? e.message ?? 'Could not complete the compliance operation.';
  }
}
