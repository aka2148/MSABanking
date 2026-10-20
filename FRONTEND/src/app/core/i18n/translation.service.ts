import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { CATALOGUES } from './translations';
import { Language, isLanguage, readLanguage, saveLanguage, formatTranslation } from './language';

@Injectable({ providedIn: 'root' })
export class TranslationService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Language>('en');
  readonly language = this.current.asReadonly();

  constructor() {
    let storage: Storage | undefined;
    try { storage = this.document.defaultView?.localStorage; } catch { /* Private browsing. */ }
    this.setLanguage(readLanguage(storage));
  }

  setLanguage(value: string): void {
    if (!isLanguage(value)) return;
    this.current.set(value);
    this.document.documentElement.lang = value;
    this.document.documentElement.dir = value === 'ar' ? 'rtl' : 'ltr';
    try { saveLanguage(value, this.document.defaultView?.localStorage); } catch { /* Private browsing. */ }
  }

  translate(value: unknown, parameters: readonly unknown[] = []): string {
    if (value === null || value === undefined) return '';
    const key = String(value);
    const translated = CATALOGUES[this.language()][key] ?? key;
    return formatTranslation(translated, parameters);
  }
}
