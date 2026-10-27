import { FdCalculator } from './fd-calculator';
import { of, throwError } from 'rxjs';
import { ProductOffer } from '../../../core/models/fd.models';
import { FdEstimate } from '../../../core/services/fd.service';

describe('FdCalculator', () => {
  const sampleProduct: ProductOffer = {
    product_id: 101,
    product_code: 'FD-STD-01',
    product_name: 'Standard Term Deposit',
    currency_offers: [
      {
        currency_code: 'INR',
        decimal_places: 2,
        minimum_amount: 10000,
        maximum_amount: 1000000,
        minimum_tenure_months: 6,
        maximum_tenure_months: 60,
        rate_tiers: [
          {
            minimum_tenure_months: 6,
            maximum_tenure_months: 11,
            annual_rate: 0.065,
            compounding_frequency: 'QUARTERLY',
            payout_frequency: 'AT_MATURITY',
          },
          {
            minimum_tenure_months: 12,
            maximum_tenure_months: 24,
            annual_rate: 0.0725,
            compounding_frequency: 'QUARTERLY',
            payout_frequency: 'AT_MATURITY',
          },
          {
            minimum_tenure_months: 25,
            maximum_tenure_months: 60,
            annual_rate: 0.075,
            compounding_frequency: 'QUARTERLY',
            payout_frequency: 'AT_MATURITY',
          },
        ],
      },
    ],
    maturity_estimate_basis: 'Quarterly compounding, Actual/365',
  };

  const sampleEstimate: FdEstimate = {
    productId: 101,
    currencyCode: 'INR',
    currencyScale: 2,
    principal: 100000,
    annualRate: 0.0725,
    compoundingBasis: 'QUARTERLY',
    payoutFrequency: 'AT_MATURITY',
    openDate: '2026-10-28',
    maturityDate: '2027-10-28',
    estimatedInterest: 7450,
    estimatedMaturityAmount: 107450,
  };

  it('initializes with default values and loads eligible products', () => {
    const fdService = {
      listProducts: () => of([sampleProduct]),
      estimate: () => of(sampleEstimate),
    };
    const route = {
      snapshot: {
        queryParamMap: new Map(),
      },
    };
    const router = { navigate: () => Promise.resolve(true) };

    const comp = new FdCalculator(fdService as never, route as never, router as never);
    comp.ngOnInit();

    expect(comp.loadingProducts).toBe(false);
    expect(comp.products.length).toBe(1);
    expect(comp.selectedProduct?.product_id).toBe(101);
    expect(comp.selectedCurrencyCode).toBe('INR');
  });

  it('handles product loading API failure gracefully', () => {
    const fdService = {
      listProducts: () => throwError(() => ({ status: 503, error: { message: 'FD Catalog service down' } })),
      estimate: () => of(sampleEstimate),
    };
    const route = {
      snapshot: {
        queryParamMap: new Map(),
      },
    };
    const router = { navigate: () => Promise.resolve(true) };

    const comp = new FdCalculator(fdService as never, route as never, router as never);
    comp.loadProducts();

    expect(comp.loadingProducts).toBe(false);
    expect(comp.products.length).toBe(0);
    expect(comp.productsError).toBe('FD Catalog service down');
  });

  it('validates principal amount against product bounds', () => {
    const fdService = {
      listProducts: () => of([sampleProduct]),
      estimate: () => of(sampleEstimate),
    };
    const route = { snapshot: { queryParamMap: new Map() } };
    const router = { navigate: () => Promise.resolve(true) };

    const comp = new FdCalculator(fdService as never, route as never, router as never);
    comp.selectProduct(sampleProduct);

    // Below minimum
    comp.principalAmount = 5000;
    comp.validateAndCalculate();
    expect(comp.validationError).toContain('Minimum deposit');

    // Above maximum
    comp.principalAmount = 2000000;
    comp.validateAndCalculate();
    expect(comp.validationError).toContain('Maximum deposit');

    // Within bounds
    comp.principalAmount = 100000;
    comp.validateAndCalculate();
    expect(comp.validationError).toBe('');
  });

  it('validates tenure bounds against currency offer limits', () => {
    const fdService = {
      listProducts: () => of([sampleProduct]),
      estimate: () => of(sampleEstimate),
    };
    const route = { snapshot: { queryParamMap: new Map() } };
    const router = { navigate: () => Promise.resolve(true) };

    const comp = new FdCalculator(fdService as never, route as never, router as never);
    comp.selectProduct(sampleProduct);

    // Below minimum tenure
    comp.tenureMonths = 3;
    comp.validateAndCalculate();
    expect(comp.validationError).toContain('Permitted tenure is 6 to 60 months');

    // Above maximum tenure
    comp.tenureMonths = 72;
    comp.validateAndCalculate();
    expect(comp.validationError).toContain('Permitted tenure is 6 to 60 months');
  });

  it('runs side-by-side product comparison and flags highest return', () => {
    const higherYieldProduct: ProductOffer = {
      ...sampleProduct,
      product_id: 102,
      product_code: 'FD-HIGH-YIELD',
      product_name: 'Special Growth FD',
    };

    const higherEstimate: FdEstimate = {
      ...sampleEstimate,
      productId: 102,
      annualRate: 0.08,
      estimatedInterest: 8243,
      estimatedMaturityAmount: 108243,
    };

    const fdService = {
      listProducts: () => of([sampleProduct, higherYieldProduct]),
      estimate: (req: { productId: number }) => {
        if (req.productId === 102) return of(higherEstimate);
        return of(sampleEstimate);
      },
    };
    const route = { snapshot: { queryParamMap: new Map() } };
    const router = { navigate: () => Promise.resolve(true) };

    const comp = new FdCalculator(fdService as never, route as never, router as never);
    comp.products = [sampleProduct, higherYieldProduct];
    comp.principalAmount = 100000;
    comp.tenureMonths = 12;
    comp.selectedCurrencyCode = 'INR';

    comp.runComparison();

    expect(comp.comparisonItems.length).toBe(2);
    const best = comp.comparisonItems.find(i => i.isBestReturn);
    expect(best?.product.product_id).toBe(102);
    expect(best?.estimate?.estimatedMaturityAmount).toBe(108243);
  });

  it('navigates to booking flow with query params', () => {
    let navigatedUrl = '';
    let navigatedExtras: unknown = null;
    const router = {
      navigate: (commands: string[], extras: unknown) => {
        navigatedUrl = commands.join('/');
        navigatedExtras = extras;
        return Promise.resolve(true);
      },
    };

    const comp = new FdCalculator({} as never, { snapshot: { queryParamMap: new Map() } } as never, router as never);
    comp.principalAmount = 150000;
    comp.tenureMonths = 24;

    comp.bookProduct(sampleProduct, 'INR');

    expect(navigatedUrl).toBe('/fd/book/101');
    expect(navigatedExtras).toEqual({
      queryParams: {
        currency: 'INR',
        amount: 150000,
        months: 24,
      },
    });
  });
});
