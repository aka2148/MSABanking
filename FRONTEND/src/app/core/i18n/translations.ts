import { Language } from './language';
import { CUSTOMER_MESSAGES } from './customer-messages';
import { DEPOSIT_MESSAGES } from './deposit-messages';
import { STAFF_MESSAGES } from './staff-messages';
import { BUILDER_MESSAGES } from './builder-messages';
import { ACTION_MESSAGES } from './action-messages';
import { NEW_MESSAGES } from './new-messages';

export const CATALOGUES: Record<Language, Record<string, string>> = {
  en: Object.create(null), hi: Object.create(null), kn: Object.create(null),
  ja: Object.create(null), ar: Object.create(null), es: Object.create(null),
};
const codes: Language[] = ['en', 'hi', 'kn', 'ja', 'ar', 'es'];
for (const row of [CUSTOMER_MESSAGES, DEPOSIT_MESSAGES, STAFF_MESSAGES, BUILDER_MESSAGES, ACTION_MESSAGES, NEW_MESSAGES].join('\n').trim().split('\n')) {
  if (!row.trim()) continue;
  const columns = row.split('|');
  const key = columns[0].trim();
  codes.forEach((code, index) => { CATALOGUES[code][key] = columns[index]?.trim() || key; });
}
