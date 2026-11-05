import en from './en.json';

export type Translations = typeof en;

/** The id is also what goes to YouTube as `hl`, so YouTube's own text follows it (#274). */
export type LocaleId = 'en';

export const translations: Record<LocaleId, Translations> = { en };
