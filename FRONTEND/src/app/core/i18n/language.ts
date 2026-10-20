export const LANGUAGES = [
  { code: 'en', name: 'English', profileName: 'English' },
  { code: 'hi', name: 'हिन्दी', profileName: 'Hindi' },
  { code: 'kn', name: 'ಕನ್ನಡ', profileName: 'Kannada' },
  { code: 'ja', name: '日本語', profileName: 'Japanese' },
  { code: 'ar', name: 'العربية', profileName: 'Arabic' },
  { code: 'es', name: 'Español', profileName: 'Spanish' },
] as const;

export type Language = typeof LANGUAGES[number]['code'];
export const LANGUAGE_STORAGE_KEY = 'moneypal.language';
export function isLanguage(value: unknown): value is Language {
  return LANGUAGES.some(language => language.code === value);
}

export function readLanguage(storage?: Pick<Storage, 'getItem'>): Language {
  try {
    const saved = storage?.getItem(LANGUAGE_STORAGE_KEY);
    return isLanguage(saved) ? saved : 'en';
  } catch { return 'en'; }
}

export function saveLanguage(language: Language, storage?: Pick<Storage, 'setItem'>): void {
  try { storage?.setItem(LANGUAGE_STORAGE_KEY, language); } catch { /* Storage can be disabled. */ }
}

export function formatTranslation(message: string, parameters: readonly unknown[]): string {
  // One pass prevents a parameter's braces from being interpreted as another placeholder.
  return message.replace(/\{(\d+)\}/g, (match, index: string) =>
    Number(index) < parameters.length ? String(parameters[Number(index)] ?? '') : match);
}
