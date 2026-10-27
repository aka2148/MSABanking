import { FdCompliance } from './fd-compliance';
import { of, throwError } from 'rxjs';
import { ProductSchedule } from '../../core/services/fd-compliance.service';
import { FdOfferAdmin } from '../../core/services/fd-product-admin.service';

describe('FdCompliance', () => {
  const sampleOffer: FdOfferAdmin = {
    catalog_product_id: 1,
    product_code: 'FD_STANDARD',
    product_name: 'Standard Fixed Deposit',
    category_id: 1,
    category_name: 'FIXED_DEPOSIT',
    status: 'ACTIVE',
    effective_from: '2026-01-01',
    effective_to: null,
    currency_codes: ['INR', 'USD'],
  };

  const sampleSchedule: ProductSchedule = {
    productId: 1,
    productCode: 'FD_STANDARD',
    productName: 'Standard Fixed Deposit',
    status: 'ACTIVE',
    effectiveFrom: '2026-01-01',
    effectiveTo: null,
    currencies: [
      { currencyCode: 'INR', minimumAmount: 10000, maximumAmount: 1000000, isBaseCurrency: true },
      { currencyCode: 'USD', minimumAmount: 100, maximumAmount: 50000, isBaseCurrency: false },
    ],
    rateBands: [
      {
        ruleId: 10,
        currencyCode: 'INR',
        minimumTenureMonths: 6,
        maximumTenureMonths: 12,
        annualRate: 0.065,
        compoundingBasis: 'QUARTERLY',
        payoutFrequency: 'AT_MATURITY',
        effectiveFrom: '2026-01-01',
        effectiveTo: null,
        isActive: true,
        status: 'ACTIVE',
      },
    ],
  };

  it('loads products and authoritative schedule on initialization', () => {
    const catalog = {
      listOffers: () => of([sampleOffer]),
    };
    const compliance = {
      clock: () => of({ business_date: '2026-10-28', system_date: '2026-10-28', business_zone: 'Asia/Kolkata' }),
      getSchedule: () => of(sampleSchedule),
    };

    const comp = new FdCompliance(compliance as never, catalog as never);
    comp.ngOnInit();

    expect(comp.allProducts.length).toBe(1);
    expect(comp.products.length).toBe(1);
    expect(comp.selectedManageProductId).toBe(1);
    expect(comp.schedule).toEqual(sampleSchedule);
    expect(comp.scheduleLoading).toBe(false);
  });

  it('populates editable bands when editing schedule', () => {
    const comp = new FdCompliance({} as never, {} as never);
    comp.schedule = sampleSchedule;

    comp.startEditing();

    expect(comp.isEditingSchedule).toBe(true);
    expect(comp.editableBands.length).toBe(1);
    expect(comp.editableBands[0].ratePercent).toBe(6.5);
    expect(comp.editableBands[0].minimumTenureMonths).toBe(6);
    expect(comp.editableBands[0].maximumTenureMonths).toBe(12);
  });

  it('rejects invalid client-side schedule edits before submitting to server', () => {
    let updateCalled = false;
    const compliance = {
      updateSchedule: () => {
        updateCalled = true;
        return of(sampleSchedule);
      },
    };
    const comp = new FdCompliance(compliance as never, {} as never);
    comp.schedule = sampleSchedule;
    comp.selectedManageProductId = 1;
    comp.startEditing();

    // Inverted tenure range
    comp.editableBands[0].minimumTenureMonths = 24;
    comp.editableBands[0].maximumTenureMonths = 12;

    comp.saveSchedule();

    expect(updateCalled).toBe(false);
    expect(comp.scheduleError).toContain('Minimum tenure must be >= 1 and <= maximum tenure');
  });

  it('submits valid schedule update and displays success banner', () => {
    let submittedPayload: unknown = null;
    const updatedSchedule: ProductSchedule = {
      ...sampleSchedule,
      rateBands: [
        {
          ...sampleSchedule.rateBands[0],
          annualRate: 0.07,
        },
      ],
    };
    const compliance = {
      updateSchedule: (id: number, payload: unknown) => {
        submittedPayload = payload;
        return of(updatedSchedule);
      },
    };

    const comp = new FdCompliance(compliance as never, {} as never);
    comp.schedule = sampleSchedule;
    comp.selectedManageProductId = 1;
    comp.startEditing();

    comp.editableBands[0].ratePercent = 7.0;
    comp.saveSchedule();

    expect(comp.scheduleSaving).toBe(false);
    expect(comp.isEditingSchedule).toBe(false);
    expect(comp.scheduleSuccess).toContain('Rate schedule validated and published successfully');
    expect(comp.schedule?.rateBands[0].annualRate).toBe(0.07);
    expect(submittedPayload).toBeDefined();
  });

  it('handles server rejection when publishing invalid schedule', () => {
    const compliance = {
      updateSchedule: () => throwError(() => ({
        status: 400,
        error: { message: 'Overlapping tenure schedules detected for currency INR' },
      })),
    };

    const comp = new FdCompliance(compliance as never, {} as never);
    comp.schedule = sampleSchedule;
    comp.selectedManageProductId = 1;
    comp.startEditing();

    comp.saveSchedule();

    expect(comp.scheduleSaving).toBe(false);
    expect(comp.isEditingSchedule).toBe(true);
    expect(comp.scheduleError).toBe('Overlapping tenure schedules detected for currency INR');
  });
});
