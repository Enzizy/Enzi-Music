// UI text. English only. Rust gets the language too, because YouTube's own text is part of it:
// see `pushLocaleToRust`.
import { invoke } from '@tauri-apps/api/core';
import { translations, type LocaleId, type Translations } from './locales';

export type { LocaleId };

/** Every dot path through the catalog: `'nav.home' | 'player.play' | …`. */
type NestedKeyOf<ObjectType extends object> = {
	[Key in keyof ObjectType & (string | number)]: ObjectType[Key] extends object
		? `${Key}` | `${Key}.${NestedKeyOf<ObjectType[Key]>}`
		: `${Key}`;
}[keyof ObjectType & (string | number)];

export type TranslationKey = NestedKeyOf<Translations>;

const activeLocale: LocaleId = 'en';

/**
 * Tell Rust which language to ask YouTube for (`hl`). Home shelf titles, the mood chips, playlist
 * subtitles and auto-playlist names are YouTube's strings, not ours (#274). Stored in SQLite there:
 * the first home fetch of the next launch happens before this module could push anything, so Rust
 * has to already know. `initApp` reconciles the two at startup.
 */
export function pushLocaleToRust(locale: LocaleId): Promise<void> {
	return invoke<void>('set_setting', { key: 'locale', value: locale }).catch(() => {});
}

export const currentLocale = {
	get id() {
		return activeLocale;
	}
};

function getNestedValue(obj: unknown, path: string): unknown {
	return path.split('.').reduce<unknown>((acc, part) => (acc as any)?.[part], obj);
}

/**
 * Look up a key. `key` is typed against the catalog, so a typo or a key that was never added is a
 * build error rather than a literal `home.remove_shortcut` rendered in the UI.
 *
 *   t('nav.home')
 *   t('settings.about.version', { version: '0.5.5' })
 */
export function t(key: TranslationKey, params?: Record<string, string | number>): string {
	const str = getNestedValue(translations[activeLocale], key);
	if (typeof str !== 'string') return key;
	if (!params) return str;
	return str.replace(/\{(\w+)\}/g, (_, k) => (params[k] !== undefined ? String(params[k]) : `{${k}}`));
}
