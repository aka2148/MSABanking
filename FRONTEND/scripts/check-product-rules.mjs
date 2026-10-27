import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import '@angular/compiler';
import { Subject, of } from 'rxjs';
import { Injector, runInInjectionContext } from '@angular/core';

process.chdir(path.resolve(import.meta.dirname, '..'));
const generated = path.resolve('.product-rules-check.mjs');
try {
  await build({
    stdin: { contents: `
      export * from './src/app/features/staff/product-builder/product-templates';
      export * from './src/app/features/staff/product-builder/product-builder';
      export * from './src/app/shared/components/customer-product-warning';
      export * from './src/app/core/services/auth.service';
    `, resolveDir: process.cwd(), loader: 'ts' },
    bundle: true, platform: 'node', format: 'esm', packages: 'external',
    outfile: generated, tsconfig: 'tsconfig.json',
  });
  const { PRODUCT_TEMPLATES, ProductBuilder,
    CustomerProductWarning, AuthService } = await import(pathToFileURL(generated));
  const metadata = { categories: [], attributes: [], currencies: [{ code: 'JPY', decimalPlaces: 0 }], roles: [{ id: 1, code: 'PRIMARY_HOLDER' }], facts: [{ code: 'VERIFIED_IDENTITY', dataType: 'BOOLEAN', operators: ['EQ','NE'], choices: ['true','false'] }] };
  const products = new Subject();
  const identity = new Subject();
  const builder = new ProductBuilder({ validateIdentity: () => identity, metadata: () => of(metadata), list: () => products });
  builder.catalogPage = 9;
  builder.load();
  assert.equal(builder.catalogLoading, true);
  assert.equal(builder.roleSelections[1], true);
  products.next([]);
  assert.equal(builder.catalogLoading, false);
  assert.equal(builder.catalogPage, 0);
  assert.equal(builder.currencyStep('JPY'), '1');
  builder.productStep = 2;
  builder.productName = '123';
  builder.effectiveFrom = '2099-01-01';
  builder.effectiveTo = '2100-01-01';
  const form = { controls: {}, invalid: false };
  builder.nextProductStep(form);
  assert.equal(builder.validatingIdentity, true);
  identity.next({ productName: 'Enter a valid product name (3-255 characters including a letter).' });
  assert.equal(builder.productStep, 2);
  assert.ok(builder.identityErrors.productName);
  builder.productName = 'Future product';
  builder.effectiveFrom = '2099-01-01';
  builder.effectiveTo = '2100-01-01';
  builder.identityEdited('productName');
  assert.equal(builder.identityErrors.productName, undefined);
  builder.effectiveFrom = '2099-01-01';
  builder.nextProductStep(form);
  identity.next({});
  assert.equal(builder.productStep, 4);
  const rule = { fact: 'VERIFIED_IDENTITY', operator: 'IN', valueFrom: 'old', valueTo: 'old', valueSet: 'old' };
  builder.eligibilityFactChanged(rule);
  assert.equal(rule.operator, 'EQ');
  assert.deepEqual([rule.valueFrom,rule.valueTo,rule.valueSet],['','','']);
  assert.deepEqual(builder.factOption(rule).operators,['EQ','NE']);
  assert.deepEqual(builder.factOption(rule).choices,['true','false']);
  builder.categoryId = 1;
  builder.applyTemplate(PRODUCT_TEMPLATES[0]);
  builder.effectiveFrom = '2099-01-01';
  builder.effectiveTo = '2100-01-01';
  assert.equal(builder.buildRequest(form).productCode, '');
  assert.equal(builder.ruleName({ fact: 'KYC_STATUS', operator: 'EQ', valueFrom: 'VERIFIED' }), 'KYC_STATUS EQ VERIFIED');
  for (const [kycStatus, riskCategory, blocked] of [
    ['PENDING','LOW',true], ['REJECTED','LOW',true], [null,'LOW',true],
    ['VERIFIED','HIGH',true], ['VERIFIED','LOW',false], ['VERIFIED','MEDIUM',false],
  ]) {
    const injector = Injector.create({ providers: [{ provide: AuthService, useValue: { customerProfile: () => of({ kycStatus, riskCategory }) } }] });
    const warning = runInInjectionContext(injector, () => new CustomerProductWarning());
    let emitted;
    warning.blockedChange.subscribe(value => emitted = value);
    warning.ngOnInit();
    assert.equal(emitted, blocked);
    assert.equal(!!warning.message, blocked);
    if (kycStatus === 'REJECTED') assert.ok(warning.message.includes('Re-apply from the KYC screen with your MPIN.'));
    if (kycStatus === 'PENDING' || kycStatus == null) assert.ok(warning.message.includes('still pending'));
    assert.ok(!warning.message.includes('undefined') && !warning.message.includes('null'));
    injector.destroy();
  }
  console.log('Backend identity validation flow, generated-code payload, fact controls, catalogue state and customer warning checks passed.');
} finally {
  if (fs.existsSync(generated)) fs.unlinkSync(generated);
}
