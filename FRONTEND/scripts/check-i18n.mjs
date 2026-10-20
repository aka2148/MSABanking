import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import '@angular/compiler';
import { Injector } from '@angular/core';
import { DOCUMENT } from '@angular/common';

process.chdir(path.resolve(import.meta.dirname, '..'));
const generated = path.resolve('.i18n-check.mjs');
try {
  await build({
    stdin: { contents: `
      export * from './src/app/core/i18n/language';
      export * from './src/app/core/i18n/translations';
      export * from './src/app/core/i18n/translation.service';
      export * from './src/app/core/i18n/translate.pipe';
    `, resolveDir: process.cwd(), loader: 'ts' },
    bundle: true, platform: 'node', format: 'esm', packages: 'external',
    outfile: generated, tsconfig: 'tsconfig.json',
  });
  const { CATALOGUES, LANGUAGES, LANGUAGE_STORAGE_KEY, readLanguage, saveLanguage,
    formatTranslation, TranslationService, TranslatePipe } = await import(pathToFileURL(generated));
  const placeholders = value => [...value.matchAll(/\{\d+\}/g)].map(m => m[0]).sort();
  const missing = new Set();
  for (const name of fs.readdirSync('src/app', { recursive: true }).filter(n => /\.(html|ts)$/.test(n) && !n.includes('i18n'))) {
    const text = fs.readFileSync(path.join('src/app', name), 'utf8');
    for (const match of text.matchAll(/'((?:\\.|[^'\\])*)'\s*\|\s*translate/g)) {
      const key = match[1].replace(/\\'/g, "'").replace(/\\\\/g, '\\');
      if (/[A-Za-z]{2}/.test(key) && !CATALOGUES.en[key]) missing.add(key);
    }
  }
  assert.deepEqual([...missing], [], 'Missing template translations');
  for (const language of LANGUAGES) {
    for (const [key, value] of Object.entries(CATALOGUES.en)) {
      assert.ok(CATALOGUES[language.code][key], `Missing ${language.code}: ${key}`);
      assert.deepEqual(placeholders(CATALOGUES[language.code][key]), placeholders(value),
        `Placeholder mismatch ${language.code}: ${key}`);
    }
  }
  // Validate source rows too, so an accidentally omitted column cannot hide behind fallback.
  for (const name of fs.readdirSync('src/app/core/i18n').filter(n => n.endsWith('-messages.ts'))) {
    const text = fs.readFileSync(`src/app/core/i18n/${name}`, 'utf8').split('`')[1];
    for (const row of text.trim().split('\n').filter(r => r.trim())) {
      assert.equal(row.split('|').length, 6, `Six columns required: ${row}`);
      assert.ok(row.split('|').every(c => c.trim()), `Empty translation: ${row}`);
    }
  }
  assert.equal(readLanguage(), 'en');
  assert.equal(readLanguage({ getItem: () => 'xx' }), 'en');
  assert.equal(readLanguage({ getItem: () => { throw Error('blocked'); } }), 'en');
  assert.doesNotThrow(() => saveLanguage('ar', { setItem: () => { throw Error('blocked'); } }));
  assert.equal(formatTranslation('{1} / {0}', ['100.00', 'INR']), 'INR / 100.00');
  assert.equal(formatTranslation('{0} {1}', ['{1}', '$&']), '{1} $&');
  const saved = new Map([[LANGUAGE_STORAGE_KEY, 'hi']]);
  const document = { documentElement: { lang: '', dir: '' }, defaultView: { localStorage: {
    getItem: key => saved.get(key), setItem: (key, value) => saved.set(key, value),
  } } };
  const injector = Injector.create({ providers: [
    { provide: DOCUMENT, useValue: document }, TranslationService, TranslatePipe,
  ] });
  const service = injector.get(TranslationService);
  const pipe = injector.get(TranslatePipe);
  assert.equal(service.language(), 'hi');
  assert.equal(pipe.transform('Sign in'), CATALOGUES.hi['Sign in']);
  for (const { code } of LANGUAGES) {
    service.setLanguage(code);
    assert.equal(document.documentElement.lang, code);
    assert.equal(document.documentElement.dir, code === 'ar' ? 'rtl' : 'ltr');
    assert.equal(saved.get(LANGUAGE_STORAGE_KEY), code);
    assert.equal(pipe.transform('Sign in'), CATALOGUES[code]['Sign in']);
  }
  service.setLanguage('unknown');
  assert.equal(service.language(), 'es');
  assert.equal(pipe.transform('An unknown server message'), 'An unknown server message');
  assert.equal(pipe.transform(null), '');
  assert.equal(pipe.transform('{0} / {1}', ['123456', '100.25 INR']), '123456 / 100.25 INR');
  const restored = Injector.create({ providers: [{ provide: DOCUMENT, useValue: document }, TranslationService] });
  assert.equal(restored.get(TranslationService).language(), 'es');
  const blockedDocument = { documentElement: {}, defaultView: {
    get localStorage() { throw Error('blocked'); },
  } };
  const blocked = Injector.create({ providers: [{ provide: DOCUMENT, useValue: blockedDocument }, TranslationService] });
  assert.equal(blocked.get(TranslationService).language(), 'en');
  blocked.get(TranslationService).setLanguage('ar');
  assert.equal(blockedDocument.documentElement.dir, 'rtl');
  console.log(`Passed: ${Object.keys(CATALOGUES.en).length} messages, six languages, placeholders, switching, persistence, RTL, fallback and blocked storage.`);
} finally {
  if (fs.existsSync(generated)) fs.unlinkSync(generated);
}
