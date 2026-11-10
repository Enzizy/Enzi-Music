// Dev-only stand-in for the Rust backend, so the interface can be laid out in a plain browser:
// `pnpm dev`, then open http://localhost:5183/?mock. It plays a made-up queue and answers every other
// command with nothing. Inside the app, and in any build, Tauri's own backend is there and this
// never runs.
import { mockIPC, mockWindows } from '@tauri-apps/api/mocks';
import { emit } from '@tauri-apps/api/event';
import type { NowPlaying, QueueState, SongItem } from './api';

/** A cover drawn in place, so the preview needs no network. */
function cover(a: string, b: string, letter: string): string {
	const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs><linearGradient id="g" x2="1" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="512" height="512" fill="url(#g)"/><text x="256" y="330" font-size="240" font-family="sans-serif" font-weight="700" fill="rgba(255,255,255,.85)" text-anchor="middle">${letter}</text></svg>`;
	return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

const songs: SongItem[] = [
	['Neon Harbor', 'Lumen Coast', '3:34', '#ff4d73', '#5e0a33', 'N'],
	['Paper Planes at Dusk', 'The Quiet Arcade', '4:02', '#4d8bff', '#0a1f5e', 'P'],
	['Saltwater Radio', 'Mara Vell', '3:11', '#2fd3a0', '#0a4a3a', 'S'],
	['Late Bus Home', 'Juno & The Lanterns', '5:20', '#ffb84d', '#5e3a0a', 'L'],
	['Glass Orchard', 'Teodora Finch', '2:48', '#b64dff', '#2e0a5e', 'G'],
	['Undertow', 'Low Tide Choir', '3:57', '#4dd5ff', '#0a3d5e', 'U']
].map(([title, artists, duration, a, b, letter], i) => ({
	video_id: `demo${i}`,
	title,
	artists,
	album: 'Night Swim',
	duration,
	thumbnail: cover(a, b, letter)
}));

const queue: QueueState = { items: songs, currentIndex: 0, sourceName: 'Night Swim' };
const now = (i: number): NowPlaying => ({
	videoId: songs[i].video_id,
	title: songs[i].title,
	artists: songs[i].artists,
	thumbnail: songs[i].thumbnail,
	duration: songs[i].duration,
	album: songs[i].album,
	streamClient: 'mock'
});

let paused = false;
let current = 0;

function play(i: number) {
	current = (i + songs.length) % songs.length;
	queue.currentIndex = current;
	void emit('queue-changed', queue);
	void emit('now-playing', now(current));
	void emit('position', { position: 0 });
}

if (
	import.meta.env.DEV &&
	typeof window !== 'undefined' &&
	!('__TAURI_INTERNALS__' in window) &&
	new URLSearchParams(location.search).has('mock')
) {
	mockWindows('main');
	mockIPC(
		(cmd) => {
			switch (cmd) {
				case 'get_playback':
					return { speed: 1, semitones: 0, reverb: { enabled: false }, now: now(current), paused, position: 52, duration: 214, volume: 80 };
				case 'get_queue':
					return queue;
				case 'get_settings':
					return {};
				case 'toggle_pause':
					paused = !paused;
					void emit('playback-state', paused ? 'paused' : 'playing');
					return null;
				case 'next_track':
					play(current + 1);
					return null;
				case 'prev_track':
					play(current - 1);
					return null;
				default:
					return null;
			}
		},
		{ shouldMockEvents: true }
	);
}
