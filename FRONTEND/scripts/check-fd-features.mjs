import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import '@angular/compiler';
import { of, throwError } from 'rxjs';

process.chdir(path.resolve(import.meta.dirname, '..'));
const generated = path.resolve('.fd-features-check.mjs');

try {
  await build({
    stdin: {
      contents: `
        export * from './src/app/features/fd/calculator/fd-calculator';
        export * from './src/app/features/compliance/fd-compliance';
        export * from './src/app/core/services/fd-compliance.service';
        export * from './src/app/core/services/fd.service';
      `,
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    bundle: true,
    platform: 'node',
    format: 'esm',
    packages: 'external',
    outfile: generated,
    tsconfig: 'tsconfig.json',
  });

  const { FdCalculator, FdCompliance } = await import(pathToFileURL(generated));

  console.log('--- Testing Feature A: Customer FD Calculator ---');

  const sampleProduct = {
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
          { minimum_tenure_months: 6, maximum_tenure_months: 11, annual_rate: 0.065, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
          { minimum_tenure_months: 12, maximum_tenure_months: 24, annual_rate: 0.0725, compounding_frequency: 'QUARTERLY', payout_frequency: 'AT_MATURITY' },
        ],
      },
    ],
    maturity_estimate_basis: 'Quarterly compounding, Actual/365',
  };

  const sampleEstimate = {
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

  // Test 1: Initialization & Loading
  {
    const fdService = {
      listProducts: () => of([sampleProduct]),
      estimate: () => of(sampleEstimate),
    };
    const route = { snapshot: { queryParamMap: new Map() } };
    const router = { navigate: () => Promise.resolve(true) };
    const calc = new FdCalculator(fdService, route, router);

    calc.ngOnInit();
    assert.equal(calc.loadingProducts, false);
    assert.equal(calc.products.length, 1);
    assert.equal(calc.selectedProduct.product_id, 101);
    assert.equal(calc.selectedCurrencyCode, 'INR');
    console.log('✓ Calculator product loading initialized successfully');
  }

  // Test 2: Product API failure handling
  {
    const fdService = {
      listProducts: () => throwError(() => ({ status: 503, error: { message: 'FD Service offline' } })),
      estimate: () => of(sampleEstimate),
    };
    const route = { snapshot: { queryParamMap: new Map() } };
    const calc = new FdCalculator(fdService, route, {});
    calc.loadProducts();

    assert.equal(calc.loadingProducts, false);
    assert.equal(calc.products.length, 0);
    assert.equal(calc.productsError, 'FD Service offline');
    console.log('✓ Calculator API failure handled gracefully');
  }

  // Test 3: Validation Bounds (Principal & Tenure)
  {
    const fdService = {
      listProducts: () => of([sampleProduct]),
      estimate: () => of(sampleEstimate),
    };
    const calc = new FdCalculator(fdService, { snapshot: { queryParamMap: new Map() } }, {});
    calc.selectProduct(sampleProduct);

    // Below minimum amount
    calc.principalAmount = 5000;
    calc.validateAndCalculate();
    assert.ok(calc.validationError.includes('Minimum deposit'), 'Should validate minimum deposit');

    // Above maximum amount
    calc.principalAmount = 2000000;
    calc.validateAndCalculate();
    assert.ok(calc.validationError.includes('Maximum deposit'), 'Should validate maximum deposit');

    // Below minimum tenure
    calc.principalAmount = 100000;
    calc.tenureMonths = 3;
    calc.validateAndCalculate();
    assert.ok(calc.validationError.includes('Permitted tenure'), 'Should validate minimum tenure');

    // Valid inputs
    calc.tenureMonths = 12;
    calc.validateAndCalculate();
    assert.equal(calc.validationError, '', 'Valid inputs should clear validation error');
    console.log('✓ Input validation for principal and tenure verified');
  }

  // Test 4: Product comparison mode & best return identification
  {
    const product2 = {
      ...sampleProduct,
      product_id: 102,
      product_code: 'FD-GROWTH',
      product_name: 'Growth Deposit',
    };
    const estimate2 = {
      ...sampleEstimate,
      productId: 102,
      annualRate: 0.08,
      estimatedInterest: 8240,
      estimatedMaturityAmount: 108240,
    };
    const fdService = {
      listProducts: () => of([sampleProduct, product2]),
      estimate: (req) => {
        if (req.productId === 102) return of(estimate2);
        return of(sampleEstimate);
      },
    };
    const calc = new FdCalculator(fdService, { snapshot: { queryParamMap: new Map() } }, {});
    calc.products = [sampleProduct, product2];
    calc.principalAmount = 100000;
    calc.tenureMonths = 12;
    calc.selectedCurrencyCode = 'INR';

    calc.runComparison();
    assert.equal(calc.comparisonItems.length, 2);
    const best = calc.comparisonItems.find(i => i.isBestReturn);
    assert.ok(best, 'A best return product should be flagged');
    assert.equal(best.product.product_id, 102);
    assert.equal(best.estimate.estimatedMaturityAmount, 108240);
    console.log('✓ Product comparison and best yield identification verified');
  }

  // Test 5: Booking navigation with query parameters
  {
    let target = '';
    let extras = null;
    const router = {
      navigate: (commands, ex) => {
        target = commands.join('/');
        extras = ex;
        return Promise.resolve(true);
      },
    };
    const calc = new FdCalculator({}, { snapshot: { queryParamMap: new Map() } }, router);
    calc.principalAmount = 250000;
    calc.tenureMonths = 24;
    calc.bookProduct(sampleProduct, 'INR');

    assert.equal(target, '/fd/book/101');
    assert.deepEqual(extras.queryParams, {
      currency: 'INR',
      amount: 250000,
      months: 24,
    });
    console.log('✓ Booking flow integration verified');
  }

  console.log('\n--- Testing Feature B: Staff FD Rate Management UI ---');

  const sampleOffer = {
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

  const sampleSchedule = {
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

  // Test 6: Schedule Loading in Compliance
  {
    const catalog = { listOffers: () => of([sampleOffer]) };
    const compliance = {
      clock: () => of({ business_date: '2026-10-28', system_date: '2026-10-28', business_zone: 'Asia/Kolkata' }),
      getSchedule: () => of(sampleSchedule),
    };
    const comp = new FdCompliance(compliance, catalog);
    comp.ngOnInit();

    assert.equal(comp.allProducts.length, 1);
    assert.equal(comp.selectedManageProductId, 1);
    assert.equal(comp.schedule.productCode, 'FD_STANDARD');
    assert.equal(comp.schedule.rateBands.length, 1);
    console.log('✓ Authoritative schedule loading in compliance verified');
  }

  // Test 7: Schedule Edit mode, Band Management & Validation
  {
    const comp = new FdCompliance({}, {});
    comp.schedule = sampleSchedule;
    comp.selectedManageProductId = 1;
    comp.startEditing();

    assert.equal(comp.isEditingSchedule, true);
    assert.equal(comp.editableBands.length, 1);
    assert.equal(comp.editableBands[0].ratePercent, 6.5);

    // Add band
    comp.addBand();
    assert.equal(comp.editableBands.length, 2);

    // Invert tenure on band 2
    comp.editableBands[1].minimumTenureMonths = 36;
    comp.editableBands[1].maximumTenureMonths = 12;

    comp.saveSchedule();
    assert.ok(comp.scheduleError.includes('Minimum tenure must be >= 1 and <= maximum tenure'));

    // Fix tenure but set invalid rate
    comp.editableBands[1].minimumTenureMonths = 13;
    comp.editableBands[1].maximumTenureMonths = 36;
    comp.editableBands[1].ratePercent = -1;

    comp.saveSchedule();
    assert.ok(comp.scheduleError.includes('Annual interest rate must be between 0.01% and 100%'));

    // Invert product dates
    comp.editableBands[1].ratePercent = 7.5;
    comp.editableProductEffectiveFrom = '2027-01-01';
    comp.editableProductEffectiveTo = '2026-01-01';

    comp.saveSchedule();
    assert.ok(comp.scheduleError.includes('Product effective-from date cannot be after effective-to date'));
    console.log('✓ Client-side schedule validation rules verified');
  }

  // Test 8: Safe Schedule Update & Publication
  {
    let sentPayload = null;
    const updatedSchedule = {
      ...sampleSchedule,
      rateBands: [{ ...sampleSchedule.rateBands[0], annualRate: 0.07 }],
    };
    const compliance = {
      updateSchedule: (id, payload) => {
        sentPayload = payload;
        return of(updatedSchedule);
      },
    };
    const comp = new FdCompliance(compliance, {});
    comp.schedule = sampleSchedule;
    comp.selectedManageProductId = 1;
    comp.startEditing();

    comp.editableBands[0].ratePercent = 7.0;
    comp.editableProductEffectiveFrom = '2026-01-01';
    comp.editableProductEffectiveTo = '2028-12-31';

    comp.saveSchedule();

    assert.equal(comp.scheduleSaving, false);
    assert.equal(comp.isEditingSchedule, false);
    assert.ok(comp.scheduleSuccess.includes('Rate schedule validated and published successfully'));
    assert.equal(sentPayload.rateBands[0].annualRate, 0.07);
    console.log('✓ Schedule update and publication verified');
  }

  console.log('\nALL FRONTEND CHECKS PASSED SUCCESSFULLY!');
} finally {
  if (fs.existsSync(generated)) {
    fs.unlinkSync(generated);
  }
}
