import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const kyc=readFileSync('src/app/features/kyc/kyc.ts','utf8');
const review=readFileSync('src/app/features/staff/kyc/kyc-review.ts','utf8');
const service=readFileSync('src/app/core/services/kyc-review.service.ts','utf8');
assert.match(kyc,/KYC_REAPPLY/); assert.match(kyc,/classifyMpinError\('verify'/); assert.match(kyc,/classifyMpinError\('operation'/);
assert.match(review,/refreshAwaiting/); assert.match(review,/q\.content\.length===0&&page>0/); assert.match(review,/generation!==this\.generation/);
assert.match(service,/awaitingCount\$/); console.log('KYC re-apply/review refresh checks passed');
