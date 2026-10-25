import { ProductFamily } from '../../../core/services/product-configuration-admin.service';

export interface ProductTemplate {
  key: string;
  name: string;
  family: ProductFamily;
  categoryCode: string;
  productCode: string;
  description: string;
  attributes: Record<string, string>;
  currencies: Array<{ code: string; minimumAmount: number; maximumAmount: number }>;
  pricing: Array<{ currencyCode: string; from: number; to: number; ratePercent: number }>;
  eligibility?: { name: string; fact: string; dataType: string; operator: string; valueFrom: string };
  limitation?: string;
}

export const FD_CURRENCY_DEFAULTS = [
  { code: 'INR', minimumAmount: 1000, maximumAmount: 10000000 },
  { code: 'JPY', minimumAmount: 10000, maximumAmount: 1000000000 },
  { code: 'KWD', minimumAmount: 10, maximumAmount: 100000 },
  { code: 'USD', minimumAmount: 100, maximumAmount: 1000000 },
];

const STANDARD_FD_PRICING = [
  { currencyCode: 'INR', from: 1, to: 5, ratePercent: 4 },
  { currencyCode: 'INR', from: 6, to: 11, ratePercent: 5.5 },
  { currencyCode: 'INR', from: 12, to: 36, ratePercent: 6.5 },
  { currencyCode: 'INR', from: 37, to: 60, ratePercent: 6.25 },
  { currencyCode: 'JPY', from: 1, to: 5, ratePercent: 0.2 },
  { currencyCode: 'JPY', from: 6, to: 11, ratePercent: 0.5 },
  { currencyCode: 'JPY', from: 12, to: 36, ratePercent: 0.75 },
  { currencyCode: 'JPY', from: 37, to: 60, ratePercent: 1 },
  { currencyCode: 'KWD', from: 1, to: 5, ratePercent: 2.5 },
  { currencyCode: 'KWD', from: 6, to: 11, ratePercent: 3.5 },
  { currencyCode: 'KWD', from: 12, to: 36, ratePercent: 4 },
  { currencyCode: 'KWD', from: 37, to: 60, ratePercent: 3.75 },
  { currencyCode: 'USD', from: 1, to: 5, ratePercent: 3 },
  { currencyCode: 'USD', from: 6, to: 11, ratePercent: 3.5 },
  { currencyCode: 'USD', from: 12, to: 36, ratePercent: 4 },
  { currencyCode: 'USD', from: 37, to: 60, ratePercent: 3.75 },
];

// Illustrative defaults for the existing Product Builder fields, not bank rates or live products.
// Category and attribute codes are resolved against Product Service metadata at selection time.
export const PRODUCT_TEMPLATES: readonly ProductTemplate[] = [
  {
    key: 'standard-fd', name: 'Standard Fixed Deposit', family: 'FIXED_DEPOSIT', categoryCode: 'FIXED_DEPOSIT',
    productCode: 'FD_STANDARD', description: 'Illustrative fixed deposit defaulting to quarterly compounding and payout at maturity for INR, JPY, KWD and USD. Compounding and payout frequency can be changed in the Terms step.',
    attributes: { COMPOUNDING_BASIS: 'QUARTERLY', PAYOUT_FREQUENCY: 'AT_MATURITY' },
    currencies: FD_CURRENCY_DEFAULTS,
    pricing: STANDARD_FD_PRICING,
  },
  {
    key: 'senior-fd', name: 'Senior Citizen Fixed Deposit', family: 'FIXED_DEPOSIT', categoryCode: 'FIXED_DEPOSIT',
    productCode: 'FD_SENIOR', description: 'Sample INR fixed deposit with a minimum tenure of 6 months, defaulting to quarterly compounding and payout at maturity. Eligibility requires customer age 60 or above. Terms can be changed in the builder.',
    attributes: { COMPOUNDING_BASIS: 'QUARTERLY', PAYOUT_FREQUENCY: 'AT_MATURITY' },
    currencies: [{ code: 'INR', minimumAmount: 1000, maximumAmount: 10000000 }],
    pricing: [
      { currencyCode: 'INR', from: 6, to: 11, ratePercent: 6 },
      { currencyCode: 'INR', from: 12, to: 36, ratePercent: 7 },
      { currencyCode: 'INR', from: 37, to: 60, ratePercent: 6.75 },
    ],
    eligibility: { name: 'Senior citizen age', fact: 'CUSTOMER_AGE', dataType: 'NUMBER', operator: 'GTE', valueFrom: '60' },
  },
  {
    key: 'tax-saver-fd', name: 'Tax Saver Fixed Deposit', family: 'FIXED_DEPOSIT', categoryCode: 'FIXED_DEPOSIT',
    productCode: 'FD_TAX_SAVER', description: 'Sample INR fixed deposit defaulting to a 60-month tenure, quarterly compounding, payout at maturity and an amount range of INR 1,000–150,000. Tax treatment and withdrawal lock-in are not enforced; terms can be changed in the builder.',
    attributes: { COMPOUNDING_BASIS: 'QUARTERLY', PAYOUT_FREQUENCY: 'AT_MATURITY' },
    currencies: [{ code: 'INR', minimumAmount: 1000, maximumAmount: 150000 }],
    pricing: [{ currencyCode: 'INR', from: 60, to: 60, ratePercent: 6.5 }],
    limitation: 'The Product Builder cannot enforce a five-year withdrawal lock-in or tax-benefit eligibility. Review before publishing as a tax-saving product.',
  },
  {
    key: 'short-fd', name: 'Short-Term Fixed Deposit', family: 'FIXED_DEPOSIT', categoryCode: 'FIXED_DEPOSIT',
    productCode: 'FD_SHORT_TERM', description: 'Sample INR fixed deposit with a 1–12 month tenure range, defaulting to quarterly compounding and payout at maturity. Compounding and payout frequency can be changed in the Terms step.',
    attributes: { COMPOUNDING_BASIS: 'QUARTERLY', PAYOUT_FREQUENCY: 'AT_MATURITY' },
    currencies: [{ code: 'INR', minimumAmount: 1000, maximumAmount: 5000000 }],
    pricing: [
      { currencyCode: 'INR', from: 1, to: 6, ratePercent: 4.5 },
      { currencyCode: 'INR', from: 7, to: 12, ratePercent: 5 },
    ],
  },
  {
    key: 'basic-savings', name: 'Basic Savings Account', family: 'CASA', categoryCode: 'SAVINGS',
    productCode: 'CASA_BASIC_SAVINGS', description: 'Sample INR savings account with a zero minimum balance and daily-balance interest calculation.',
    attributes: { MINIMUM_BALANCE: '0', INTEREST_CALCULATION: 'DAILY_BALANCE' },
    currencies: [{ code: 'INR', minimumAmount: 0, maximumAmount: 10000000 }],
    pricing: [{ currencyCode: 'INR', from: 0, to: 10000000, ratePercent: 2.5 }],
  },
  {
    key: 'premium-savings', name: 'Premium Savings Account', family: 'CASA', categoryCode: 'SAVINGS',
    productCode: 'CASA_PREMIUM_SAVINGS', description: 'Sample INR savings account with a minimum balance attribute of INR 10,000, daily-balance interest calculation and a higher sample rate tier beginning at INR 100,000.',
    attributes: { MINIMUM_BALANCE: '10000', INTEREST_CALCULATION: 'DAILY_BALANCE' },
    currencies: [{ code: 'INR', minimumAmount: 0, maximumAmount: 10000000 }],
    pricing: [
      { currencyCode: 'INR', from: 0, to: 99999.99, ratePercent: 2.5 },
      { currencyCode: 'INR', from: 100000, to: 10000000, ratePercent: 3.5 },
    ],
  },
  {
    key: 'business-current', name: 'Business Current Account', family: 'CASA', categoryCode: 'CURRENT',
    productCode: 'CASA_BUSINESS_CURRENT', description: 'Sample INR business current account with a minimum balance attribute of INR 10,000 and a zero sample interest rate using daily-balance calculation.',
    attributes: { MINIMUM_BALANCE: '10000', INTEREST_CALCULATION: 'DAILY_BALANCE' },
    currencies: [{ code: 'INR', minimumAmount: 0, maximumAmount: 50000000 }],
    pricing: [{ currencyCode: 'INR', from: 0, to: 50000000, ratePercent: 0 }],
  },
  {
    key: 'personal-loan', name: 'Personal Loan', family: 'LOAN', categoryCode: 'LOAN',
    productCode: 'LOAN_PERSONAL', description: 'Sample INR personal loan for amounts INR 50,000–2,000,000 and tenures of 12–60 months, with reducing-balance repayment.',
    attributes: { MIN_AMOUNT: '50000', MAX_AMOUNT: '2000000', MIN_TENURE_MONTHS: '12', MAX_TENURE_MONTHS: '60', REPAYMENT_METHOD: 'REDUCING_BALANCE' },
    currencies: [{ code: 'INR', minimumAmount: 50000, maximumAmount: 2000000 }],
    pricing: [
      { currencyCode: 'INR', from: 12, to: 36, ratePercent: 11.5 },
      { currencyCode: 'INR', from: 37, to: 60, ratePercent: 12 },
    ],
  },
  {
    key: 'home-loan', name: 'Home Loan', family: 'LOAN', categoryCode: 'LOAN',
    productCode: 'LOAN_HOME', description: 'Sample INR home loan for amounts INR 500,000–10,000,000 and tenures of 60–240 months, with reducing-balance repayment.',
    attributes: { MIN_AMOUNT: '500000', MAX_AMOUNT: '10000000', MIN_TENURE_MONTHS: '60', MAX_TENURE_MONTHS: '240', REPAYMENT_METHOD: 'REDUCING_BALANCE' },
    currencies: [{ code: 'INR', minimumAmount: 500000, maximumAmount: 10000000 }],
    pricing: [{ currencyCode: 'INR', from: 60, to: 240, ratePercent: 8.5 }],
  },
  {
    key: 'forex-remittance', name: 'Forex Remittance', family: 'FOREX', categoryCode: 'FOREX',
    productCode: 'FOREX_REMIT_USD', description: 'Sample INR-funded remittance to USD using a supplied reference rate.',
    attributes: { FUNDING_CURRENCY: 'INR', DESTINATION_CURRENCIES: 'USD' },
    currencies: [
      { code: 'INR', minimumAmount: 1000, maximumAmount: 1000000 },
      { code: 'USD', minimumAmount: 10, maximumAmount: 12000 },
    ],
    pricing: [{ currencyCode: 'USD', from: 1000, to: 1000000, ratePercent: 1 }],
    limitation: 'Conversion preview requires a supplied reference rate; live exchange-rate lookup is not part of Product Service.',
  },
];
