export const ID_PATTERNS: Record<string, RegExp> = {
  AADHAAR: /^[2-9][0-9]{11}$/, PAN: /^[A-Z]{5}[0-9]{4}[A-Z]$/,
  PASSPORT: /^[A-Z][0-9]{7}$/, DRIVING_LICENSE: /^[A-Z0-9-]{8,20}$/, VOTER_ID: /^[A-Z]{3}[0-9]{7}$/,
};
export function validId(type: string, value: string): boolean { return !!ID_PATTERNS[type]?.test(value.trim()); }
export const COUNTRY_CODES = 'AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW'.split(' ');
export function countryOptions(language: string) {
  let names: Intl.DisplayNames;
  try { names = new Intl.DisplayNames([language, 'en'], { type: 'region' }); }
  catch { names = new Intl.DisplayNames(['en'], { type: 'region' }); }
  return COUNTRY_CODES.map(value => ({ value, label: names.of(value) || value })).sort((a, b) => a.label.localeCompare(b.label, language));
}
export function ringOffset(percent: number): number { return 100 - Math.min(100, Math.max(0, percent)); }
