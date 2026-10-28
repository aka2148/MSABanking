import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { KycProgressRing } from '../../shared/components/kyc-progress-ring/kyc-progress-ring';
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { MpinProofService } from '../../core/services/mpin-proof.service';
import { KycReviewService } from '../../core/services/kyc-review.service';

interface FrankfurterLatest {
  amount: number;
  base: string;
  date: string;
  rates: Record<string, number>;
}

interface MarketRate {
  pair: string;
  value: number;
}

@Component({
  selector: 'app-dashboard-stub',
  standalone: true,
  imports: [
    TranslatePipe,
    CommonModule,
    RouterLink,
    WorkspaceShell,
    KycProgressRing
  ],
  template: `
    <app-workspace-shell>
      <main class="workspace-home">

        <!-- HERO -->
        <header class="workspace-home__hero">
          <div class="workspace-home__hero-content">
            <div class="workspace-home__eyebrow">
              <span class="workspace-home__status-dot"></span>
              {{ 'MoneyPal workspace' | translate }}
            </div>

            <h1>
              {{
                (canManageProducts ? 'Staff workspace' : 'Welcome back')
                  | translate
              }}<span class="workspace-home__gold">.</span>
            </h1>

            <p class="workspace-home__intro">
              {{
                (canManageProducts
                  ? 'Choose an area to continue.'
                  : 'Manage savings and fixed deposits.')
                  | translate
              }}
            </p>
          </div>

          <div class="workspace-home__hero-art" aria-hidden="true">
            <div class="workspace-home__orbit workspace-home__orbit--outer"></div>
            <div class="workspace-home__orbit workspace-home__orbit--inner"></div>

            <div class="workspace-home__coin">
              <span>🌐</span>
            </div>

            <div class="workspace-home__spark workspace-home__spark--one"></div>
            <div class="workspace-home__spark workspace-home__spark--two"></div>
            <div class="workspace-home__spark workspace-home__spark--three"></div>
          </div>

          <div class="workspace-home__hero-index" aria-hidden="true">
            {{ 'MP / 01' | translate }}
          </div>
        </header>

        <!-- COMPACT CURRENCY WATCH -->
        <section
          class="workspace-home__markets"
          [attr.aria-label]="'Global markets' | translate"
        >
          <div class="workspace-home__markets-heading">
            <div>
              <p class="workspace-home__section-label">
                {{ 'Global markets' | translate }}
              </p>

              <h2>
                {{ 'Currency watch' | translate
                }}<span class="workspace-home__gold">.</span>
              </h2>

              <p class="workspace-home__markets-subtitle">
                {{ 'Reference exchange rates and global finance brief' | translate }}
              </p>
            </div>

            <div class="workspace-home__market-actions">
              <span class="workspace-home__market-source">
                <span class="workspace-home__status-dot"></span>
                {{
                  (marketLoading
                    ? 'Updating'
                    : (marketDate
                      ? 'Daily reference rates'
                      : 'Rate data unavailable'))
                    | translate
                }}
              </span>
            </div>
          </div>

          <div
            class="workspace-home__ticker"
            *ngIf="marketRates.length; else ratesUnavailable"
            [attr.aria-label]="'Exchange-rate ticker' | translate"
          >
            <div class="workspace-home__ticker-track">
              <!--
                The strip is repeated 8 times (an even number) so the content is always
                wider than the visible area. The track slides by exactly half of its
                width, which is a whole number of groups, so the loop is seamless and
                the right side never runs empty.
              -->
              <div
                class="workspace-home__ticker-group"
                *ngFor="let pass of [0, 1, 2, 3, 4, 5, 6, 7]"
                [attr.aria-hidden]="pass > 0 ? 'true' : null"
              >
                <div
                  class="workspace-home__ticker-item"
                  *ngFor="let rate of marketRates"
                >
                  <span class="workspace-home__ticker-pair">
                    {{ rate.pair }}
                  </span>

                  <strong>
                    {{ rate.value | number:'1.2-4' }}
                  </strong>

                  <span class="workspace-home__ticker-unit">
                    {{ 'Reference' | translate }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <ng-template #ratesUnavailable>
            <div class="workspace-home__market-message">
              {{
                (marketLoading
                  ? 'Fetching reference rates…'
                  : 'Exchange rates could not be loaded. Please try again later.')
                  | translate
              }}
            </div>
          </ng-template>

          <p
            class="workspace-home__data-note workspace-home__rate-date"
            *ngIf="marketDate"
          >
            {{ 'Reference rates dated {0} · Source: Frankfurter' | translate: [marketDate] }}
          </p>
        </section>

        <!-- KYC LEFT + FINANCIAL NEWS RIGHT -->
        <section class="workspace-home__overview" *ngIf="!canManageProducts">

          <!-- EXISTING KYC COMPONENT -->
          <section
            class="workspace-home__kyc"
            *ngIf="!canManageProducts"
          >
            <div class="workspace-home__section-heading">
              <div>
                <p class="workspace-home__section-label">
                  {{ 'Your profile' | translate }}
                </p>

                <h2>
                  {{ 'KYC completion' | translate
                  }}<span class="workspace-home__gold">.</span>
                </h2>

                <p class="workspace-home__section-subtitle">
                  {{ 'Track your identity verification progress.' | translate }}
                </p>
              </div>
            </div>

            <div class="workspace-home__kyc-panel">
              <app-kyc-progress-ring></app-kyc-progress-ring>
            </div>
          </section>

          <!-- FINANCIAL NEWS -->
          <section class="workspace-home__news-section">
            <div class="workspace-home__section-heading">
              <div>
                <p class="workspace-home__section-label">
                  {{ 'The world of money' | translate }}
                </p>

                <h2>
                  {{ 'Financial news desk' | translate
                  }}<span class="workspace-home__gold">.</span>
                </h2>
              </div>

              <span class="workspace-home__news-icon" aria-hidden="true">
                ↗
              </span>
            </div>

            <div class="workspace-home__news-grid">
              <a
                class="workspace-home__news-link"
                href="https://www.reuters.com/markets/"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="workspace-home__news-marker"></span>
                <span class="workspace-home__news-copy">
                  <strong>{{ 'Global markets' | translate }}</strong>
                  <small>
                    {{ 'Markets, currencies and economic developments · Reuters' | translate }}
                  </small>
                </span>
                <span class="workspace-home__news-arrow">↗</span>
              </a>

              <a
                class="workspace-home__news-link"
                href="https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="workspace-home__news-marker"></span>
                <span class="workspace-home__news-copy">
                  <strong>{{ 'India monetary policy' | translate }}</strong>
                  <small>
                    {{ 'Official releases and policy updates · Reserve Bank of India' | translate }}
                  </small>
                </span>
                <span class="workspace-home__news-arrow">↗</span>
              </a>

              <a
                class="workspace-home__news-link"
                href="https://www.ecb.europa.eu/press/html/index.en.html"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="workspace-home__news-marker"></span>
                <span class="workspace-home__news-copy">
                  <strong>{{ 'European Central Bank' | translate }}</strong>
                  <small>
                    {{ 'Rates, inflation and euro-area announcements · ECB' | translate }}
                  </small>
                </span>
                <span class="workspace-home__news-arrow">↗</span>
              </a>

              <a
                class="workspace-home__news-link"
                href="https://www.imf.org/en/News"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="workspace-home__news-marker"></span>
                <span class="workspace-home__news-copy">
                  <strong>{{ 'Global economic outlook' | translate }}</strong>
                  <small>
                    {{ 'Economic forecasts, financial stability and policy updates · IMF' | translate }}
                  </small>
                </span>
                <span class="workspace-home__news-arrow">↗</span>
              </a>

              <a
                class="workspace-home__news-link"
                href="https://www.worldbank.org/en/news"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="workspace-home__news-marker"></span>
                <span class="workspace-home__news-copy">
                  <strong>
                    {{ 'Development and emerging markets' | translate }}
                  </strong>
                  <small>
                    {{ 'Global development, growth and emerging-market updates · World Bank' | translate }}
                  </small>
                </span>
                <span class="workspace-home__news-arrow">↗</span>
              </a>

              <a
                class="workspace-home__news-link"
                href="https://www.cnbc.com/finance/"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="workspace-home__news-marker"></span>
                <span class="workspace-home__news-copy">
                  <strong>
                    {{ 'Business and financial markets' | translate }}
                  </strong>
                  <small>
                    {{ 'Business headlines, market movements and investor coverage · CNBC' | translate }}
                  </small>
                </span>
                <span class="workspace-home__news-arrow">↗</span>
              </a>
            </div>

            <p class="workspace-home__data-note">
              {{
                "Opens the publishers' latest coverage. Headlines are not embedded in MoneyPal."
                  | translate
              }}
            </p>
          </section>

        </section>

        <!-- EXISTING MPIN SETUP -->
        <section
          class="workspace-home__mpin glass-card"
          *ngIf="!canManageProducts && !mpinSet"
        >
          <div class="workspace-home__mpin-copy">
            <p class="workspace-home__section-label">
              {{ 'Account security' | translate }}
            </p>

            <h2>{{ 'Set up your MPIN' | translate }}</h2>

            <p>
              {{ 'Set up an MPIN to authorize financial actions.' | translate }}
            </p>
          </div>

          <a class="glass-btn" routerLink="/mpin">
            {{ 'Set up your MPIN' | translate }}
          </a>
        </section>

        <!-- EXISTING QUICK-ACCESS LINKS -->
        <section class="workspace-home__quick-access">
          <div class="workspace-home__section-heading">
            <div>
              <p class="workspace-home__section-label">
                {{
                  (canManageProducts ? 'Administration' : 'Your finances')
                    | translate
                }}
              </p>

              <h2>
                {{ 'Quick access' | translate
                }}<span class="workspace-home__gold">.</span>
              </h2>
            </div>

            <span class="workspace-home__section-count">
              {{ (canManageProducts ? (isAdmin ? '08' : '07') : '04') }}
              <span>{{ 'Areas' | translate }}</span>
            </span>
          </div>

          <nav
            class="workspace-home__links"
            [attr.aria-label]="'Workspace shortcuts' | translate"
          >
            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/kyc"
              style="--card-index: 0"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">✓</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'KYC review' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Awaiting review' | translate }}: {{ awaitingReview ?? '—' }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Review applications' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="!canManageProducts"
              class="workspace-home__card"
              routerLink="/savings"
              style="--card-index: 0"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">⌂</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Savings accounts' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Open and manage savings' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Explore accounts' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a *ngIf="!canManageProducts" class="workspace-home__card" routerLink="/current-accounts" style="--card-index: 1">
              <span class="workspace-home__card-top"><span class="workspace-home__card-icon" aria-hidden="true">CA</span><span class="workspace-home__arrow" aria-hidden="true">↗</span></span>
              <span class="workspace-home__card-title">{{ 'Current accounts' | translate }}</span>
              <span class="workspace-home__card-description">{{ 'Browse and open a current account.' | translate }}</span>
              <span class="workspace-home__card-footer"><span>{{ 'Explore accounts' | translate }}</span><span class="workspace-home__card-line"></span></span>
            </a>

            <a
              *ngIf="!canManageProducts"
              class="workspace-home__card"
              routerLink="/fd"
              style="--card-index: 1"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">₹</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Fixed deposits' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Browse eligible offers' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Explore offers' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="!canManageProducts"
              class="workspace-home__card"
              routerLink="/fd/accounts"
              style="--card-index: 2"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">▤</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'My deposits' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'View account activity' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'View deposits' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="!canManageProducts"
              class="workspace-home__card"
              routerLink="/reports"
              style="--card-index: 3"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">▤</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'FD reports' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Review interest history, statements and notices' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Review reports' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/product-builder"
              [queryParams]="{ new: 1 }"
              style="--card-index: 0"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">＋</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Product configuration' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{
                  'Create products from templates or a custom setup' | translate
                }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Configure products' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/fd-products"
              style="--card-index: 1"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">▤</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'FD offer management' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{
                  'Activate and update existing fixed-deposit offers' | translate
                }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Manage offers' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/customers"
              style="--card-index: 2"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">♙</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Customers' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Profiles and KYC status' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'View customers' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/fd"
              style="--card-index: 3"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">▣</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'FD accounts' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Deposit status and maturity' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Manage accounts' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/reports"
              style="--card-index: 4"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">▤</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'FD reports' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{
                  'Review account summaries, statements and notices' | translate
                }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Review reports' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/staff/savings"
              style="--card-index: 5"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">⌂</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Savings operations' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Balances and teller activity' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Open operations' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="canManageProducts"
              class="workspace-home__card"
              routerLink="/compliance"
              style="--card-index: 6"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">✓</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Compliance' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Rates and effective dates' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Review compliance' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>

            <a
              *ngIf="isAdmin"
              class="workspace-home__card"
              routerLink="/staff/operations/clock"
              style="--card-index: 7"
            >
              <span class="workspace-home__card-top">
                <span class="workspace-home__card-icon" aria-hidden="true">◷</span>
                <span class="workspace-home__arrow" aria-hidden="true">↗</span>
              </span>
              <span class="workspace-home__card-title">
                {{ 'Operations' | translate }}
              </span>
              <span class="workspace-home__card-description">
                {{ 'Administrative tools' | translate }}
              </span>
              <span class="workspace-home__card-footer">
                <span>{{ 'Open operations' | translate }}</span>
                <span class="workspace-home__card-line"></span>
              </span>
            </a>
          </nav>
        </section>

      </main>
    </app-workspace-shell>
  `,
  styles: [`
    :host {
      display: block;
      color: var(--ws-text);
    }

    .workspace-home {
      position: relative;
      isolation: isolate;
      width: calc(100% - clamp(24px, 4vw, 72px));
      max-width: 1760px;
      box-sizing: border-box;
      margin: 0 auto;
      padding: 4px 0 32px;
      animation: workspace-enter 650ms cubic-bezier(.2, .7, .2, 1) both;
    }

    /* HERO */

    .workspace-home__hero {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 178px;
      overflow: hidden;
      padding: clamp(22px, 3vw, 34px) clamp(24px, 4vw, 44px);
      border: 1px solid rgba(229, 187, 104, .24);
      border-radius: 22px;
      background:
        radial-gradient(ellipse at 82% 50%, rgba(193, 145, 62, .13), transparent 38%),
        linear-gradient(115deg, #171612 0%, #11110f 52%, #1b1811 100%);
      box-shadow:
        0 18px 55px rgba(0, 0, 0, .14),
        inset 0 1px 0 rgba(255, 255, 255, .035);
    }

    .workspace-home__hero::before {
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgba(229, 187, 104, .035) 1px, transparent 1px),
        linear-gradient(90deg, rgba(229, 187, 104, .035) 1px, transparent 1px);
      background-size: 38px 38px;
      content: '';
      mask-image: linear-gradient(90deg, transparent, #000 70%);
      pointer-events: none;
    }

    .workspace-home__hero-content {
      position: relative;
      z-index: 2;
      max-width: 520px;
    }

    .workspace-home__eyebrow {
      display: inline-flex;
      align-items: center;
      gap: 9px;
      margin-bottom: 12px;
      color: #d7bd83;
      font-size: .7rem;
      font-weight: 650;
      letter-spacing: .17em;
      text-transform: uppercase;
    }

    .workspace-home__status-dot {
      display: inline-block;
      flex: 0 0 7px;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #d6b66e;
      box-shadow: 0 0 12px rgba(214, 182, 110, .6);
    }

    .workspace-home__hero h1 {
      margin: 0;
      color: #f7f3e9;
      font-size: clamp(2rem, 3.6vw, 3rem);
      font-weight: 550;
      letter-spacing: -.055em;
      line-height: 1.12;
    }

    .workspace-home__gold {
      color: #d9b66b;
    }

    .workspace-home__intro {
      max-width: 420px;
      margin: 10px 0 0;
      color: #aaa69b;
      font-size: .98rem;
      line-height: 1.7;
    }

    .workspace-home__hero-note {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
      color: #d0c5a9;
      font-size: .73rem;
      letter-spacing: .045em;
    }

    .workspace-home__note-line {
      width: 30px;
      height: 1px;
      background: #c9a85d;
    }

    .workspace-home__hero-art {
      position: absolute;
      top: 50%;
      right: clamp(22px, 8vw, 110px);
      width: 190px;
      height: 190px;
      transform: translateY(-50%);
      pointer-events: none;
    }

    .workspace-home__orbit {
      position: absolute;
      top: 50%;
      left: 50%;
      border: 1px solid rgba(216, 182, 106, .23);
      border-radius: 50%;
      transform: translate(-50%, -50%);
    }

    .workspace-home__orbit--outer {
      width: 220px;
      height: 220px;
      animation: workspace-orbit 36s linear infinite;
    }

    .workspace-home__orbit--inner {
      width: 165px;
      height: 165px;
      border-style: dashed;
      border-color: rgba(216, 182, 106, .3);
      animation: workspace-orbit 28s linear infinite reverse;
    }

    .workspace-home__coin {
      position: absolute;
      top: 50%;
      left: 50%;
      display: grid;
      width: 91px;
      height: 91px;
      place-items: center;
      border: 1px solid rgba(255, 232, 176, .8);
      border-radius: 50%;
      background: radial-gradient(circle at 32% 24%, #e9d39a, #b38a3e 65%, #755522);
      box-shadow:
        0 0 0 8px rgba(206, 166, 85, .06),
        0 0 45px rgba(207, 167, 84, .18),
        inset 0 0 0 5px rgba(83, 59, 22, .13);
      transform: translate(-50%, -50%);
      animation: workspace-float 5s ease-in-out infinite;
    }

    .workspace-home__coin span {
      font-size: 3.2rem;
      line-height: 1;
    }

    .workspace-home__spark {
      position: absolute;
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: #e9cc83;
      box-shadow: 0 0 13px rgba(233, 204, 131, .9);
    }

    .workspace-home__spark--one {
      top: 18%;
      left: 22%;
    }

    .workspace-home__spark--two {
      right: 5%;
      bottom: 27%;
      width: 4px;
      height: 4px;
    }

    .workspace-home__spark--three {
      top: 13%;
      right: 25%;
      width: 3px;
      height: 3px;
    }

    .workspace-home__hero-index {
      position: absolute;
      right: 20px;
      bottom: 17px;
      color: rgba(222, 199, 148, .48);
      font-size: .62rem;
      letter-spacing: .16em;
    }

    /* SHARED SECTION STYLING */

    .workspace-home__section-heading,
    .workspace-home__markets-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
    }

    .workspace-home__section-heading {
      margin-bottom: 16px;
    }

    .workspace-home__section-label {
      margin: 0 0 7px;
      color: var(--ws-muted);
      font-size: .65rem;
      font-weight: 700;
      letter-spacing: .17em;
      text-transform: uppercase;
    }

    .workspace-home__section-heading h2,
    .workspace-home__markets-heading h2 {
      margin: 0;
      font-family: var(--font-display, inherit);
      font-size: clamp(1.25rem, 2vw, 1.65rem);
      font-weight: 550;
      letter-spacing: -.04em;
      line-height: 1.2;
    }

    .workspace-home__section-subtitle,
    .workspace-home__markets-subtitle {
      margin: 7px 0 0;
      color: var(--ws-muted);
      font-size: .78rem;
      line-height: 1.55;
    }

    /* COMPACT CURRENCY WATCH */

    .workspace-home__markets {
      margin-top: 18px;
      padding: 20px 24px 15px;
      overflow: hidden;
      border: 1px solid var(--ws-line);
      border-radius: 17px;
      background:
        linear-gradient(135deg, rgba(205, 168, 92, .045), transparent 48%),
        var(--ws-surface);
    }

    .workspace-home__markets-heading {
      margin-bottom: 14px;
    }

    .workspace-home__market-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      flex-wrap: wrap;
      gap: 10px;
    }

    .workspace-home__market-source {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      color: #c9a65e;
      font-size: .58rem;
      font-weight: 700;
      letter-spacing: .09em;
      text-align: right;
      text-transform: uppercase;
      white-space: nowrap;
    }

    .workspace-home__ticker {
      overflow: hidden;
      border: 1px solid rgba(205, 168, 92, .2);
      border-radius: 10px;
      background: rgba(0, 0, 0, .15);
    }

    /*
      The track holds 8 identical groups and slides by 50% (4 groups), so the
      loop is seamless. 60s keeps the scroll speed comfortable (~55px/s) while the
      first item comes back around much sooner than before.
    */
    .workspace-home__ticker-track {
      display: flex;
      width: max-content;
      animation: workspace-ticker 60s linear infinite;
    }

    .workspace-home__ticker:hover .workspace-home__ticker-track {
      animation-play-state: paused;
    }

    .workspace-home__ticker-group {
      display: flex;
      flex-shrink: 0;
    }

    .workspace-home__ticker-item {
      display: flex;
      min-width: 0;
      align-items: center;
      justify-content: center;
      gap: 9px;
      padding: 12px 17px;
      border-right: 1px solid var(--ws-line);
      white-space: nowrap;
    }

    .workspace-home__ticker-pair {
      color: var(--ws-muted);
      font-size: .68rem;
    }

    .workspace-home__ticker-item strong {
      color: var(--ws-text);
      font-size: .82rem;
      font-variant-numeric: tabular-nums;
    }

    .workspace-home__ticker-unit {
      color: #b79b5b;
      font-size: .54rem;
      letter-spacing: .06em;
      text-transform: uppercase;
    }

    .workspace-home__data-note {
      margin: 11px 0 0;
      color: var(--ws-muted);
      font-size: .63rem;
      line-height: 1.55;
    }

    .workspace-home__rate-date {
      margin-bottom: 0;
    }

    .workspace-home__market-message {
      padding: 16px 4px;
      color: var(--ws-muted);
      font-size: .8rem;
    }

    /* KYC LEFT + NEWS RIGHT */

    .workspace-home__overview {
      display: grid;
      grid-template-columns: minmax(270px, .82fr) minmax(0, 1.65fr);
      align-items: stretch;
      gap: 18px;
      margin-top: 22px;
    }

    .workspace-home__kyc,
    .workspace-home__news-section {
      min-width: 0;
      margin: 0;
      padding: 22px;
      border: 1px solid var(--ws-line);
      border-radius: 17px;
      background: var(--ws-surface);
    }

    .workspace-home__kyc {
      display: flex;
      flex-direction: column;
    }

    .workspace-home__kyc .workspace-home__section-heading {
      margin-bottom: 12px;
    }

    .workspace-home__kyc-panel {
      display: flex;
      flex: 1;
      min-height: 200px;
      align-items: center;
      justify-content: center;
      padding: 14px;
      border: 1px solid rgba(205, 168, 92, .16);
      border-radius: 13px;
      background:
        radial-gradient(circle at 50% 45%, rgba(205, 168, 92, .07), transparent 68%),
        rgba(0, 0, 0, .08);
    }

    .workspace-home__news-section {
      display: flex;
      flex-direction: column;
    }

    .workspace-home__news-section .workspace-home__section-heading {
      margin-bottom: 10px;
    }

    .workspace-home__news-icon {
      color: #c9a65e;
      font-size: 1.25rem;
    }

    .workspace-home__news-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      column-gap: 24px;
    }

    .workspace-home__news-link {
      display: grid;
      grid-template-columns: 7px minmax(0, 1fr) auto;
      align-items: start;
      gap: 10px;
      padding: 11px 2px;
      border-top: 1px solid var(--ws-line);
      color: var(--ws-text);
      text-decoration: none;
      transition: background 180ms ease;
    }

    .workspace-home__news-link:focus-visible {
      outline: 2px solid #d5b060;
      outline-offset: 3px;
      border-radius: 4px;
    }

    .workspace-home__news-marker {
      width: 6px;
      height: 6px;
      margin-top: 6px;
      border-radius: 50%;
      background: #c9a65e;
      box-shadow: 0 0 10px rgba(201, 166, 94, .35);
    }

    .workspace-home__news-copy {
      min-width: 0;
    }

    .workspace-home__news-link strong {
      display: block;
      font-size: .77rem;
      font-weight: 600;
      line-height: 1.45;
    }

    .workspace-home__news-link small {
      display: block;
      margin-top: 4px;
      color: var(--ws-muted);
      font-size: .66rem;
      line-height: 1.5;
    }

    .workspace-home__news-arrow {
      color: var(--ws-muted);
      transition: transform 180ms ease, color 180ms ease;
    }

    .workspace-home__news-link:hover strong,
    .workspace-home__news-link:hover .workspace-home__news-arrow {
      color: #c9a65e;
    }

    .workspace-home__news-link:hover .workspace-home__news-arrow {
      transform: translate(2px, -2px);
    }

    /* MPIN SETUP */

    .workspace-home__mpin {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 24px;
      margin-top: 22px;
      padding: 22px 26px;
      border: 1px solid var(--ws-line);
      border-radius: 16px;
      background:
        linear-gradient(120deg, rgba(205, 168, 92, .07), transparent 65%),
        var(--ws-surface);
    }

    .workspace-home__mpin-copy h2 {
      margin: 0;
      font-family: var(--font-display, inherit);
      font-size: 1.15rem;
      font-weight: 600;
    }

    .workspace-home__mpin-copy > p:last-child {
      margin: 8px 0 0;
      color: var(--ws-muted);
      font-size: .85rem;
      line-height: 1.6;
    }

    .workspace-home__mpin .glass-btn {
      flex-shrink: 0;
    }

    /* QUICK ACCESS */

    .workspace-home__quick-access {
      margin-top: 30px;
    }

    .workspace-home__quick-access > .workspace-home__section-heading {
      margin-bottom: 15px;
    }

    .workspace-home__section-count {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-bottom: 4px;
      color: var(--ws-text);
      font-family: var(--font-display, inherit);
      font-size: .95rem;
    }

    .workspace-home__section-count span {
      color: var(--ws-muted);
      font-family: var(--font-body, inherit);
      font-size: .59rem;
      letter-spacing: .12em;
      text-transform: uppercase;
    }

    /* Four cards per row on desktop */
    .workspace-home__links {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 13px;
    }

    .workspace-home__card {
      position: relative;
      display: flex;
      min-width: 0;
      min-height: 185px;
      flex-direction: column;
      align-items: stretch;
      padding: 17px;
      overflow: hidden;
      border: 1px solid var(--ws-line);
      border-radius: 15px;
      background: var(--ws-surface);
      color: var(--ws-text);
      text-decoration: none;
      animation: workspace-card-enter 550ms cubic-bezier(.2, .7, .2, 1) both;
      animation-delay: calc(var(--card-index, 0) * 65ms + 100ms);
      transition:
        transform 220ms cubic-bezier(.2, .7, .2, 1),
        border-color 220ms ease,
        background 220ms ease,
        box-shadow 220ms ease;
    }

    .workspace-home__card::before {
      position: absolute;
      top: 0;
      left: 17px;
      width: 35px;
      height: 2px;
      background: #c7a35b;
      content: '';
      opacity: .7;
      transition: width 220ms ease, opacity 220ms ease;
    }

    .workspace-home__card:hover {
      z-index: 1;
      transform: translateY(-4px);
      border-color: rgba(213, 176, 96, .52);
      background: var(--ws-surface-raised);
      box-shadow: 0 14px 30px rgba(0, 0, 0, .09);
    }

    .workspace-home__card:hover::before {
      width: 65px;
      opacity: 1;
    }

    .workspace-home__card:focus-visible {
      outline: 2px solid #d5b060;
      outline-offset: 4px;
    }

    .workspace-home__card-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 17px;
    }

    .workspace-home__card-icon {
      display: grid;
      width: 38px;
      height: 38px;
      place-items: center;
      border: 1px solid rgba(205, 168, 92, .27);
      border-radius: 11px;
      background: rgba(205, 168, 92, .07);
      color: #c9a65e;
      font-size: 1.1rem;
      transition: background 220ms ease, transform 220ms ease;
    }

    .workspace-home__card:hover .workspace-home__card-icon {
      transform: translateY(-2px);
      background: rgba(205, 168, 92, .14);
    }

    .workspace-home__arrow {
      color: var(--ws-muted);
      font-size: 1.05rem;
      transition: color 220ms ease, transform 220ms ease;
    }

    .workspace-home__card:hover .workspace-home__arrow {
      transform: translate(2px, -2px);
      color: #c9a65e;
    }

    .workspace-home__card-title {
      font-family: var(--font-display, inherit);
      font-size: .94rem;
      font-weight: 600;
      letter-spacing: -.015em;
      line-height: 1.4;
      overflow-wrap: anywhere;
    }

    .workspace-home__card-description {
      margin-top: 6px;
      color: var(--ws-muted);
      font-size: .74rem;
      line-height: 1.5;
      overflow-wrap: anywhere;
    }

    .workspace-home__card-footer {
      display: flex;
      align-items: center;
      gap: 9px;
      margin-top: auto;
      padding-top: 17px;
      color: var(--ws-muted);
      font-size: .64rem;
      letter-spacing: .025em;
    }

    .workspace-home__card-line {
      width: 22px;
      height: 1px;
      flex-shrink: 0;
      background: rgba(205, 168, 92, .55);
      transition: width 220ms ease;
    }

    .workspace-home__card:hover .workspace-home__card-line {
      width: 36px;
    }

    /* ANIMATIONS */

    @keyframes workspace-ticker {
      from { transform: translateX(0); }
      to { transform: translateX(-50%); }
    }

    @keyframes workspace-enter {
      from { opacity: 0; transform: translateY(9px); }
      to { opacity: 1; transform: translateY(0); }
    }

    @keyframes workspace-card-enter {
      from { opacity: 0; transform: translateY(12px); }
      to { opacity: 1; transform: translateY(0); }
    }

    @keyframes workspace-float {
      0%, 100% { transform: translate(-50%, -50%); }
      50% { transform: translate(-50%, calc(-50% - 7px)); }
    }

    @keyframes workspace-orbit {
      to { rotate: 360deg; }
    }

    /* RESPONSIVE */

    @media (max-width: 1100px) {
      .workspace-home__overview {
        grid-template-columns: minmax(230px, .8fr) minmax(0, 1.5fr);
        gap: 14px;
      }

      .workspace-home__kyc,
      .workspace-home__news-section {
        padding: 18px;
      }

      .workspace-home__links {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .workspace-home__news-grid {
        column-gap: 16px;
      }

      .workspace-home__ticker-item {
        padding-right: 14px;
        padding-left: 14px;
      }
    }

    @media (max-width: 760px) {
      .workspace-home {
        width: calc(100% - 32px);
      }

      .workspace-home__hero-art {
        right: 16px;
        opacity: .4;
        transform: translate(12%, -50%) scale(.8);
      }

      .workspace-home__hero-content {
        max-width: 76%;
      }

      .workspace-home__markets-heading {
        align-items: flex-start;
        flex-direction: row;
      }

      .workspace-home__market-actions {
        justify-content: flex-end;
      }

      .workspace-home__market-source {
        text-align: right;
      }

      .workspace-home__overview {
        grid-template-columns: minmax(0, 1fr);
      }

      .workspace-home__kyc-panel {
        min-height: 180px;
      }

      .workspace-home__news-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .workspace-home__mpin {
        align-items: flex-start;
        flex-direction: column;
      }
    }

    @media (max-width: 600px) {
      .workspace-home {
        width: calc(100% - 20px);
        padding-top: 0;
      }

      .workspace-home__hero {
        min-height: 185px;
        padding: 23px 20px;
        border-radius: 17px;
      }

      .workspace-home__hero-art {
        right: -65px;
        opacity: .23;
        transform: translateY(-50%) scale(.78);
      }

      .workspace-home__hero-content {
        max-width: 100%;
      }

      .workspace-home__eyebrow {
        margin-bottom: 12px;
        font-size: .62rem;
      }

      .workspace-home__hero h1 {
        max-width: 330px;
        font-size: clamp(1.9rem, 8vw, 2.45rem);
      }

      .workspace-home__intro {
        max-width: 270px;
        font-size: .88rem;
      }

      .workspace-home__hero-note {
        margin-top: 18px;
        font-size: .68rem;
      }

      .workspace-home__markets,
      .workspace-home__news-section,
      .workspace-home__kyc {
        padding: 18px 14px;
        border-radius: 16px;
      }

      .workspace-home__markets-heading {
        gap: 10px;
      }

      .workspace-home__market-source {
        max-width: 130px;
        white-space: normal;
        line-height: 1.5;
      }

      .workspace-home__ticker-item {
        padding: 12px 13px;
      }

      .workspace-home__ticker-pair {
        font-size: .64rem;
      }

      .workspace-home__ticker-item strong {
        font-size: .78rem;
      }

      .workspace-home__ticker-unit {
        font-size: .5rem;
      }

      .workspace-home__section-heading h2,
      .workspace-home__markets-heading h2 {
        font-size: 1.4rem;
      }

      .workspace-home__news-grid {
        grid-template-columns: minmax(0, 1fr);
      }

      .workspace-home__links {
        grid-template-columns: minmax(0, 1fr);
        gap: 10px;
      }

      .workspace-home__card {
        min-height: 165px;
        padding: 18px;
      }

      .workspace-home__card-top {
        margin-bottom: 15px;
      }

      .workspace-home__card-footer {
        padding-top: 15px;
      }

      .workspace-home__quick-access {
        margin-top: 28px;
      }

      .workspace-home__overview {
        margin-top: 16px;
      }

      .workspace-home__kyc-panel {
        min-height: 160px;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .workspace-home,
      .workspace-home__card,
      .workspace-home__coin,
      .workspace-home__orbit,
      .workspace-home__ticker-track {
        animation: none !important;
      }

      .workspace-home__card,
      .workspace-home__card::before,
      .workspace-home__card-icon,
      .workspace-home__arrow,
      .workspace-home__card-line,
      .workspace-home__news-arrow {
        transition: none !important;
      }
    }
  `]
})
export class DashboardStub implements OnInit {
  mpinSet = true;
  awaitingReview: number | null = null;

  marketLoading = true;
  marketDate = '';
  marketRates: MarketRate[] = [];

  private readonly marketApi = 'https://api.frankfurter.dev/v1';

  constructor(
    private readonly auth: AuthService,
    private readonly mpin: MpinProofService,
    private readonly kycReview: KycReviewService
  ) {}

  ngOnInit(): void {
    if (!this.canManageProducts) {
      this.mpin.status().subscribe({
        next: s => this.mpinSet = s.set,
        error: () => {}
      });
    } else {
      this.kycReview.awaitingCount$.subscribe(n => this.awaitingReview = n);
      this.kycReview.refreshAwaiting();
    }

    this.loadMarketRates();
  }

  // Use native fetch so the app session token is not sent to the public rates provider.
  private loadMarketRates(): void {
    fetch(this.marketApi + '/latest?base=USD&symbols=INR,EUR,GBP,JPY,KWD')
      .then(response => {
        if (!response.ok) throw new Error('Frankfurter request failed');
        return response.json() as Promise<FrankfurterLatest>;
      })
      .then(data => {
        const rates = data.rates ?? {};
        const usdInr = rates['INR'];
        const eurUsd = rates['EUR'];
        const gbpUsd = rates['GBP'];
        const jpyUsd = rates['JPY'];
        const kwdUsd = rates['KWD'];

        this.marketDate = data.date;
        this.marketRates = [
          { pair: 'USD / INR', value: usdInr },
          { pair: 'EUR / INR', value: usdInr / eurUsd },
          { pair: 'GBP / INR', value: usdInr / gbpUsd },
          { pair: 'USD / JPY', value: jpyUsd },
          { pair: 'KWD / INR', value: usdInr / kwdUsd }
        ].filter(rate => Number.isFinite(rate.value) && rate.value > 0);

        this.marketLoading = false;
      })
      .catch(() => {
        this.marketRates = [];
        this.marketLoading = false;
      });
  }

  get isAdmin(): boolean {
    return this.auth.currentRole() === 'ROLE_ADMIN';
  }

  get canManageProducts(): boolean {
    return ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(
      this.auth.currentRole() ?? ''
    );
  }
}
