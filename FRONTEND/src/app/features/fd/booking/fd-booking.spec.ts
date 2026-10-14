import { FdBooking } from './fd-booking';
import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import { ProductOffer } from '../../../core/models/fd.models';

describe('FdBooking', () => {
  it('uses the three-step journey and keeps submission state separate from review data', () => {
    const component = new FdBooking({} as never, {} as never, {} as never, new FormBuilder());

    expect(component.step).toBe(1);
    expect(component.submittedAccount).toBeNull();
    expect(component.bookingError).toBe('');
  });

  it('loads the selected rate band and only offers exact terms inside that band', () => {
    const offer: ProductOffer = {
      product_id: 7,
      product_code: 'FD-12',
      product_name: 'Academic Saver',
      currency_offers: [{
        currency_code: 'INR', minimum_amount: 1000, maximum_amount: 100000,
        minimum_tenure_months: 3, maximum_tenure_months: 24,
        rate_tiers: [
          { minimum_tenure_months: 3, maximum_tenure_months: 5,
            annual_rate: 0.04, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
          { minimum_tenure_months: 6, maximum_tenure_months: 12,
            annual_rate: 0.05, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
        ],
      }],
      maturity_estimate_basis: 'Quarterly compounding, Actual/365',
    };
    const query = new Map<string, string>([
      ['currency', 'INR'], ['minMonths', '6'], ['maxMonths', '12'],
    ]);
    const route = {
      snapshot: {
        paramMap: { get: () => '7' },
        queryParamMap: { get: (key: string) => query.get(key) ?? null },
      },
    };
    const component = new FdBooking({ listProducts: () => of([offer]) } as never,
      {} as never, route as never, new FormBuilder());

    component.ngOnInit();

    expect(component.selectedCurrencyOffer?.currency_code).toBe('INR');
    expect(component.selectedRateTier?.annual_rate).toBe(0.05);
    expect(component.tenureOptions.map((option) => option.value)).toEqual(['12']);
  });
});
