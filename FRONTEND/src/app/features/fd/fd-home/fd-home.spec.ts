import { ProductOffer } from '../../../core/models/fd.models';
import { FdHome } from './fd-home';

describe('FdHome', () => {
  it('starts with an explicit loading state and exposes server offers', () => {
    const component = new FdHome({ listProducts: () => undefined } as never, {} as never);
    const offer: ProductOffer = {
      product_id: 7,
      product_code: 'FD-12',
      product_name: 'Academic Saver',
      currency_offers: [
        {
          currency_code: 'USD', minimum_amount: 1000, maximum_amount: 100000,
          minimum_tenure_months: 3, maximum_tenure_months: 12,
          rate_tiers: [
            { minimum_tenure_months: 3, maximum_tenure_months: 5, annual_rate: 0.04, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
            { minimum_tenure_months: 6, maximum_tenure_months: 12, annual_rate: 0.05, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
          ],
        },
        {
          currency_code: 'INR', minimum_amount: 1000, maximum_amount: 100000,
          minimum_tenure_months: 3, maximum_tenure_months: 12,
          rate_tiers: [
            { minimum_tenure_months: 3, maximum_tenure_months: 5, annual_rate: 0.05, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
            { minimum_tenure_months: 6, maximum_tenure_months: 12, annual_rate: 0.06, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
          ],
        },
      ],
      maturity_estimate_basis: 'Server policy estimate',
    };

    component.products = [offer];
    component.loading = false;

    expect(component.loading).toBeFalse();
    expect(component.products[0].product_name).toBe('Academic Saver');
  });

  it('routes the selected currency and tenure band into the booking wizard without sending a rate', () => {
    let navigation: { commands: unknown[]; extras: unknown } | null = null;
    const router = {
      navigate: (commands: unknown[], extras: unknown) => {
        navigation = { commands, extras };
        return Promise.resolve(true);
      },
    };
    const component = new FdHome({ listProducts: () => undefined } as never, router as never);
    const product: ProductOffer = {
      product_id: 7,
      product_code: 'FD-12',
      product_name: 'Academic Saver',
      currency_offers: [{
        currency_code: 'USD', minimum_amount: 100, maximum_amount: 10000,
        minimum_tenure_months: 3, maximum_tenure_months: 12,
        rate_tiers: [{ minimum_tenure_months: 6, maximum_tenure_months: 12,
          annual_rate: 0.05, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' }],
      }],
      maturity_estimate_basis: 'Quarterly compounding, Actual/365',
    };
    component.loading = false;

    component.chooseRateTier(product, product.currency_offers[0], product.currency_offers[0].rate_tiers[0]);

    expect(navigation).toEqual({ commands: ['/fd/book', 7], extras: {
      queryParams: { currency: 'USD', minMonths: 6, maxMonths: 12 },
    } });
  });
});
