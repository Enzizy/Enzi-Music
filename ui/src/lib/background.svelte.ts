// A picture of the user's choosing behind the whole window (Settings → Appearance).
//
// The image itself lives in Rust's app data folder (`background.img`), copied in, so it outlives
// the original file and works the same with an Android content URI. It comes back as raw bytes
// and is shown through a blob URL, so the webview needs no file permission for it. The look
// (dim, blur) is a UI preference and sits in localStorage beside the theme.
//
// While one is set, <html> carries `bg-image`, which turns the shell's own surfaces (titlebar,
// sidebar, player bar) see-through in layout.css.

import { backgroundImage, setBackgroundImage } from './api';
import { logUi } from './errlog';

const KEY = 'background-look';
const CLASS = 'bg-image';
/** Long edge the picked image is scaled down to: sharp on a 1440p screen, small on disk. */
const MAX_EDGE = 2560;

export const background = $state<{ src: string | null; dim: number; blur: number }>({
	src: null,
	dim: 55,
	blur: 0
});

function show(src: string | null): void {
	if (background.src) URL.revokeObjectURL(background.src);
	background.src = src;
	document.documentElement.classList.toggle(CLASS, !!src);
}

/** Load the stored image and look at startup. */
export async function initBackground(): Promise<void> {
	try {
		const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}');
		if (typeof saved?.dim === 'number') background.dim = Math.min(90, Math.max(0, saved.dim));
		if (typeof saved?.blur === 'number') background.blur = Math.min(40, Math.max(0, saved.blur));
	} catch {
		// unparseable — keep the defaults
	}
	try {
		const bytes = await backgroundImage();
		if (bytes.byteLength) show(URL.createObjectURL(new Blob([bytes], { type: 'image/jpeg' })));
	} catch (e) {
		logUi('warn', `background image: ${e}`);
	}
}

export function setBackgroundLook(patch: Partial<{ dim: number; blur: number }>): void {
	Object.assign(background, patch);
	localStorage.setItem(KEY, JSON.stringify({ dim: background.dim, blur: background.blur }));
}

/** Scale the picked image down and re-encode it, so a 40 MB phone photo is stored as ~1 MB. */
async function shrink(file: Blob): Promise<Blob> {
	const bmp = await createImageBitmap(file);
	const scale = Math.min(1, MAX_EDGE / Math.max(bmp.width, bmp.height));
	const w = Math.max(1, Math.round(bmp.width * scale));
	const h = Math.max(1, Math.round(bmp.height * scale));
	const canvas = document.createElement('canvas');
	canvas.width = w;
	canvas.height = h;
	const ctx = canvas.getContext('2d');
	if (!ctx) throw new Error('no canvas');
	// JPEG has no transparency: a see-through PNG gets black behind it, not JPEG's default.
	ctx.fillStyle = '#000';
	ctx.fillRect(0, 0, w, h);
	ctx.drawImage(bmp, 0, 0, w, h);
	bmp.close();
	const out = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/jpeg', 0.9));
	if (!out) throw new Error("couldn't encode the image");
	return out;
}

/** Use `file` as the background. Throws with a readable message if it isn't an image. */
export async function chooseBackground(file: File): Promise<void> {
	let img: Blob;
	try {
		img = await shrink(file);
	} catch {
		throw new Error("That file isn't an image this app can read.");
	}
	await setBackgroundImage(new Uint8Array(await img.arrayBuffer()));
	show(URL.createObjectURL(img));
}

export async function clearBackground(): Promise<void> {
	await setBackgroundImage(new Uint8Array(0));
	show(null);
}
