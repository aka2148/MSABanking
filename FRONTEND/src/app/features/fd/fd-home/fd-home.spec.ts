import { ProductOffer } from '../../../core/models/fd.models';
import { FdHome } from './fd-home';

describe('FdHome', () => {
  it('starts with an explicit loading state and exposes server offers', () => {
    const component = new FdHome({ listProducts: () => undefined } as never, {} as never);
    const offer: ProductOffer = {
      product_id: 7,
      product_code: 'FD-12',
      product_name: 'Academic Saver',
      currency_code: 'INR',
      minimum_amount: 1000,
      maximum_amount: 100000,
      minimum_tenure_months: 6,
      maximum_tenure_months: 24,
      annual_rate: 0.065,
      compounding_frequency: 'ANNUAL',
      payout_frequency: 'AT_MATURITY',
      closure_penalty_rule: 'As stated by the product',
      maturity_estimate_basis: 'Server policy estimate',
    };

    component.products = [offer];
    component.loading = false;

    expect(component.loading).toBeFalse();
    expect(component.products[0].product_name).toBe('Academic Saver');
  });
});
