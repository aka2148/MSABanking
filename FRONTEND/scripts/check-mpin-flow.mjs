import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import '@angular/compiler';

process.chdir(path.resolve(import.meta.dirname, '..'));
const generated = path.resolve('.mpin-flow-check.mjs');
try {
  await build({
    stdin: { contents: "export { weakMpin } from './src/app/core/services/mpin-proof.service';", resolveDir: process.cwd(), loader: 'ts' },
    bundle: true, platform: 'node', format: 'esm', packages: 'external', outfile: generated,
  });
  const { weakMpin } = await import(pathToFileURL(generated));
  for (const pin of ['', '12345', '000000', '123456', '654321', '121212', '123123']) assert.equal(weakMpin(pin), true, `Expected weak MPIN: ${pin}`);
  for (const pin of ['482916', '907314', '135790']) assert.equal(weakMpin(pin), false, `Expected acceptable MPIN: ${pin}`);

  const read = (file) => fs.readFileSync(file, 'utf8');
  const requiredFlows = [
    ['src/app/features/savings/savings-accounts.ts', /requestProof\('TRANSFER'[\s\S]*?transferSavings\(/],
    ['src/app/features/fd/booking/fd-booking.ts', /requestProof\('FD_FUNDING'[\s\S]*?\.fund\(/],
    ['src/app/features/loans/loan-page.ts', /requestProof\('LOAN_APPLICATION'[\s\S]*?\.apply\(/],
    ['src/app/features/forex/forex-page.ts', /requestProof\('FOREX_REQUEST'[\s\S]*?\.requestForex\(/],
  ];
  for (const [file, flow] of requiredFlows) assert.match(read(file), flow, `Missing verify-before-action flow in ${file}`);
  assert.match(read('src/app/features/fd/account-detail/fd-account-detail.ts'), /requestProof\(purpose,[\s\S]*?run\(r\.proofToken\)/, 'FD close and premature-closure actions should use the MPIN proof helper');
  assert.match(read('src/app/features/kyc/kyc.ts'), /rekyc\(this\.remarks,this\.rekycMpin\)[\s\S]*?this\.rekycMpin=''/, 'Re-KYC should submit and clear the in-memory MPIN');
  assert.match(read('src/app/features/auth/mpin/mpin-page.ts'), /clearPin\(\)/, 'MPIN setup/change should clear local PIN fields after attempts');
  assert.doesNotMatch(read('src/app/features/staff/accounts/staff-account-dashboard.ts'), /Customer MPIN|verifyCustomerMpin/, 'Teller withdrawal must not request customer MPIN');
  assert.match(read('src/app/features/staff/accounts/staff-account-dashboard.ts'), /verificationMethods|Identity verified by/, 'Teller withdrawal should use identity attestation');
  console.log('MPIN strength checks, verify-before-action wiring, teller verification, and local PIN clearing passed.');
} finally {
  if (fs.existsSync(generated)) fs.unlinkSync(generated);
}
