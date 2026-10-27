import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const select = readFileSync('src/app/shared/glass-select/glass-select.ts', 'utf8');
const placement = readFileSync('src/app/shared/glass-select/overlay-placement.ts', 'utf8');
assert.match(select, /document\.body\.appendChild/);
assert.match(select, /position\s*=\s*'fixed'/);
assert.match(select, /computeOverlayPlacement/);
assert.match(placement, /viewport/);
assert.match(placement, /scrollX|scrollY/);
console.log('glass-select overlay checks passed');
