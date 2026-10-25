import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import '@angular/compiler';
import { Injector, runInInjectionContext, signal, DestroyRef } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

process.chdir(path.resolve(import.meta.dirname, '..'));
const generated = path.resolve('.kyc-check.mjs');
try {
  await build({ stdin: { contents: `
    export * from './src/app/features/kyc/kyc';
    export * from './src/app/features/auth/signup/signup';
    export * from './src/app/core/services/kyc.service';
    export * from './src/app/core/services/auth.service';
    export * from './src/app/core/i18n/translation.service';
    export * from './src/app/core/i18n/translations';
    export * from './src/app/shared/kyc-validation';
    export * from './src/app/shared/components/kyc-progress-ring/kyc-progress-ring';
  `, resolveDir: process.cwd(), loader: 'ts' }, bundle: true, platform: 'node', format: 'esm', packages: 'external', outfile: generated, tsconfig: 'tsconfig.json' });
  const { Kyc, Signup, KycService, AuthService, TranslationService, KycProgressRing, validId, COUNTRY_CODES, countryOptions, ringOffset, CATALOGUES } = await import(pathToFileURL(generated));
  for (const [type, value] of Object.entries({ AADHAAR: '234567890123', PAN: 'ABCDE1234F', PASSPORT: 'A1234567', DRIVING_LICENSE: 'KA-12345678', VOTER_ID: 'ABC1234567' })) {
    assert.ok(validId(type, value)); assert.ok(!validId(type, `!${value}`));
  }
  assert.ok(!validId('AADHAAR', '123456789012'));
  assert.equal(COUNTRY_CODES.length, 249);
  for (const lang of ['en', 'hi', 'kn', 'ja', 'ar', 'es']) assert.equal(countryOptions(lang).length, 249);
  for (const percent of [0, 11, 22, 88, 100]) assert.equal(ringOffset(percent), 100 - percent);
  const base = { kycStatus: 'PENDING', cycleNumber: 1, cycleStatus: 'OPEN', completionPercent: 0, completedFields: {}, values: {}, fieldErrors: {}, editingAllowed: true, proofNumberMasked: null, accountDetails: {}, document: null };
  const calls = [], responses = [], nav = [];
  let submitCalls = 0, submittedProfile;
  let current = structuredClone(base);
  let role = 'ROLE_CUSTOMER';
  let rekycRemarks;
  const api = { get: () => of(current), save: draft => { calls.push(draft); const response = new Subject(); responses.push(response); return response; }, submit: () => { submitCalls++; return of(submittedProfile); }, rekyc: remarks => { rekycRemarks = remarks; return of({ ...base, cycleNumber: 2, values: { gender: 'FEMALE' }, proofNumberMasked: '****234F' }); } };
  const auth = { currentRole: () => role, signup: value => { calls.push(value); return of({ success: true }); } };
  const router = { navigate: value => { nav.push(value); return Promise.resolve(true); } };
  const translations = { language: signal('en'), translate: value => value, setLanguage: () => {} };
  const injector = Injector.create({ providers: [
    { provide: KycService, useValue: api }, { provide: AuthService, useValue: auth },
    { provide: Router, useValue: router }, { provide: TranslationService, useValue: translations },
    { provide: DestroyRef, useValue: { onDestroy: () => () => {} } },
  ] });
  const page = () => runInInjectionContext(injector, () => new Kyc());
  const reply = value => { const response = responses.shift(); response.next(value); response.complete(); };
  const p = page(); p.ngOnInit();
  assert.equal(p.loading, false);
  p.changed('gender', 'FEMALE');
  await new Promise(resolve => setTimeout(resolve, 300)); assert.equal(calls.length, 0);
  p.changed('gender', 'MALE');
  await new Promise(resolve => setTimeout(resolve, 850)); assert.deepEqual(calls.shift(), { gender: 'MALE' }); assert.equal(submitCalls, 0);
  p.changed('gender', 'OTHER'); p.flush();
  reply({ ...base, values: { gender: 'MALE' }, completionPercent: 11 });
  assert.equal(p.form.gender, 'OTHER'); assert.deepEqual(calls.shift(), { gender: 'OTHER' });
  reply({ ...base, values: { gender: 'OTHER' }, completionPercent: 11 });
  p.changed('fatherOrSpouseName', '<invalid>'); p.flush(); calls.shift();
  reply({ ...base, values: { gender: 'OTHER' }, fieldErrors: { fatherOrSpouseName: 'Invalid name' } });
  assert.equal(p.form.fatherOrSpouseName, '<invalid>'); assert.equal(p.errors.fatherOrSpouseName, 'Invalid name');
  p.changed('occupation', 'STUDENT'); p.flush(); calls.shift();
  reply({ ...base, values: { occupation: 'STUDENT' } });
  assert.equal(p.form.fatherOrSpouseName, '<invalid>'); assert.equal(p.errors.fatherOrSpouseName, 'Invalid name');
  p.changed('countryOfOrigin', 'IN'); p.ngOnDestroy(); assert.deepEqual(calls.shift(), { countryOfOrigin: 'IN' });
  reply({ ...base, values: { countryOfOrigin: 'IN' } });
  const leaving = page(); leaving.ngOnInit(); leaving.changed('gender', 'MALE');
  let navigated = false; const navigation = leaving.beforeLeave().then(() => { navigated = true; });
  assert.deepEqual(calls.shift(), { gender: 'MALE' }); assert.equal(navigated, false);
  reply({ ...base, values: { gender: 'MALE' } }); await navigation; assert.equal(navigated, true); leaving.ngOnDestroy();
  current = { ...base, values: { dateOfBirth: '1990-01-01', gender: 'FEMALE', relationship: 'FATHER', fatherOrSpouseName: 'Test Parent', occupation: 'SALARIED', countryOfOrigin: 'IN', proofOfIdType: 'PAN', idIssueDate: '2010-01-01' }, proofNumberMasked: '****234F', completionPercent: 100 };
  const confirm = page(); confirm.ngOnInit(); confirm.saveAll();
  const full = calls.shift(); assert.equal(full.confirmSavedProof, true); assert.ok(!('proofOfIdValue' in full));
  reply({ ...current, kycStatus: 'PENDING', cycleStatus: 'OPEN', editingAllowed: true });
  assert.equal(submitCalls, 0); assert.equal(confirm.verified, false); assert.equal(confirm.profile.kycStatus, 'PENDING');
  confirm.changed('fatherOrSpouseName', 'Updated Parent');
  submittedProfile = { ...current, kycStatus: 'VERIFIED', cycleStatus: 'COMPLETED', editingAllowed: false, values: { ...current.values, fatherOrSpouseName: 'Updated Parent' } };
  const submission = confirm.submitKyc();
  assert.deepEqual(calls.shift(), { fatherOrSpouseName: 'Updated Parent' }); assert.equal(submitCalls, 0);
  reply({ ...current, values: { ...current.values, fatherOrSpouseName: 'Updated Parent' }, completionPercent: 100 });
  await submission;
  assert.equal(submitCalls, 1); assert.equal(confirm.verified, true); assert.equal(confirm.profile.kycStatus, 'VERIFIED');
  confirm.remarks = 'Confirm my details'; confirm.requestRekyc();
  assert.equal(rekycRemarks, 'Confirm my details'); assert.equal(confirm.profile.completionPercent, 0); assert.equal(confirm.form.gender, 'FEMALE');
  confirm.ngOnDestroy();
  current = { ...base, kycStatus: 'REJECTED', editingAllowed: false };
  const rejected = page(); rejected.ngOnInit(); rejected.changed('gender', 'MALE'); rejected.flush(); assert.equal(calls.length, 0); rejected.ngOnDestroy();
  role = 'ROLE_BANK_OFFICER'; const staff = page(); staff.ngOnInit(); assert.deepEqual(nav.pop(), ['/dashboard']); staff.ngOnDestroy(); role = 'ROLE_CUSTOMER';

  globalThis.window = { matchMedia: () => ({ matches: true }) };
  globalThis.cancelAnimationFrame = () => {};
  current = { ...base, kycStatus: 'VERIFIED', completionPercent: 0 };
  const ring = runInInjectionContext(injector, () => new KycProgressRing()); ring.ngOnInit(); assert.equal(ring.percent, 100); assert.equal(ring.offset, 0); ring.ngOnDestroy();
  const frames = []; globalThis.requestAnimationFrame = callback => { frames.push(callback); return frames.length; };
  window.matchMedia = () => ({ matches: false }); current = { ...base, completionPercent: 44 };
  const animated = runInInjectionContext(injector, () => new KycProgressRing()); animated.ngOnInit(); assert.equal(animated.offset, 100); frames.shift()(); frames.shift()(); assert.equal(animated.offset, 56); animated.ngOnDestroy();
  api.get = () => throwError(() => new Error('offline'));
  const offline = runInInjectionContext(injector, () => new KycProgressRing()); offline.ngOnInit(); assert.equal(offline.loading, false); assert.equal(offline.profile, null);

  const signup = runInInjectionContext(injector, () => new Signup(new FormBuilder(), auth, router));
  assert.deepEqual(signup.stepLabels, ['Basic details', 'Account setup']);
  assert.deepEqual(Object.keys(signup.personalGroup.controls), ['fullName', 'preferredLanguage']);
  assert.equal(signup.form.get('documents'), null);
  signup.personalGroup.patchValue({ fullName: 'Demo Customer' }); signup.next(); assert.equal(signup.currentStep, 1); assert.equal(signup.isLastStep, true);
  signup.accountGroup.patchValue({ homeBranchId: 'MUM001', username: 'demo', mobileNumber: '9000000000', email: 'demo@example.test', addressLine: 'Main road', state: 'Maharashtra', city: 'Mumbai', postalCode: '400001', password: 'ZebraMango482!', confirmPassword: 'ZebraMango482!' });
  signup.emailVerified = true;
  assert.equal(signup.form.invalid, false);
  assert.equal(signup.passwordsMismatch, false);
  assert.deepEqual(signup.passwordIssues, []);
  signup.submit(); assert.equal(calls.length, 1, signup.errorMessage); const payload = calls.pop();
  for (const field of ['dateOfBirth', 'gender', 'occupation', 'countryOfOrigin', 'fatherOrSpouseName', 'proofOfIdType', 'proofOfIdValue', 'startDate']) assert.ok(!(field in payload));
  assert.deepEqual(nav.pop(), ['/login']);
  signup.accountGroup.patchValue({ preferredCurrency: 'JPY' }); assert.equal(signup.focalX, 88.8); assert.equal(signup.focalY, 33.5);
  const ringSource = fs.readFileSync('src/app/shared/components/kyc-progress-ring/kyc-progress-ring.ts', 'utf8');
  assert.ok(ringSource.includes('@media (prefers-reduced-motion: reduce)'));
  const translationKeys = new Set(['Basic details', ...Object.values(p.labels), ...p.accountFields.map(f => f.label), ...Object.values(p.options).flat().map(o => o.label)]);
  for (const filename of ['src/app/features/kyc/kyc.html', 'src/app/shared/components/kyc-progress-ring/kyc-progress-ring.ts']) {
    const source = fs.readFileSync(filename, 'utf8');
    for (const match of source.matchAll(/'([^']+)'\s*\|\s*translate/g)) translationKeys.add(match[1]);
  }
  for (const key of translationKeys) for (const lang of ['en', 'hi', 'kn', 'ja', 'ar', 'es']) assert.ok(CATALOGUES[lang][key], `Missing KYC translation ${lang}: ${key}`);
  const kycHtml = fs.readFileSync('src/app/features/kyc/kyc.html', 'utf8');
  assert.ok(kycHtml.includes('type="button" class="glass-btn" (click)="submitKyc()"'));
  assert.ok(kycHtml.includes('(ngSubmit)="saveAll()"'));
  const dashboardSource = fs.readFileSync('src/app/features/dashboard/dashboard-stub.ts', 'utf8');
  assert.ok(dashboardSource.includes("import { KycProgressRing }"));
  assert.ok(dashboardSource.includes('<app-kyc-progress-ring *ngIf="!canManageProducts"></app-kyc-progress-ring>'));
  console.log('Passed KYC validators, countries, autosave without submission, serialized writes, stale responses, inline errors, navigation flush, masked confirmation, explicit submit after pending saves, page states, re-KYC, dashboard ring, ring animation/reduced motion, and signup flow.');
} finally { if (fs.existsSync(generated)) fs.unlinkSync(generated); }
