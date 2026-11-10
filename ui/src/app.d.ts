// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	namespace App {
		// interface Error {}
		// interface Locals {}
		// interface PageData {}
		interface PageState {
			/** The phone's full-screen player is open (MobileNowPlaying): Back closes it. */
			player?: boolean;
			/** Its queue or lyrics sheet is open: Back closes that first. */
			sheet?: boolean;
		}
		// interface Platform {}
	}
}

export {};
