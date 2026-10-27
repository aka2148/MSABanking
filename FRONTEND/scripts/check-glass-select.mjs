import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const select = readFileSync('src/app/shared/components/glass-select/glass-select.ts', 'utf8');
const style = readFileSync('src/app/shared/components/glass-select/glass-select.scss', 'utf8');
const placement = readFileSync('src/app/shared/components/glass-select/overlay-placement.ts', 'utf8');
assert.match(select, /document\.body\.appendChild/);
assert.match(style, /\.glass-select__menu[\s\S]*?position:\s*fixed/);
assert.match(select, /overlayPlacement/);
assert.match(placement, /viewportWidth|viewportHeight/);
assert.match(placement, /visible/);
console.log('glass-select overlay checks passed');
