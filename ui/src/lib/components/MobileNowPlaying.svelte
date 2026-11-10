<script lang="ts">
	// The phone's full-screen player: cover, track, seek bar and transport, with the queue and the
	// lyrics in a sheet over it. The desktop now-playing view is built for a wide window with side
	// panels; this is the same player arranged for a portrait screen.
	//
	// Android's back button closes it. Opening pushes a shallow history entry (page.state.player),
	// so back pops that entry instead of leaving the page underneath. The sheet does the same, so
	// back closes the sheet before the player.
	import { onMount } from 'svelte';
	import { fly, fade } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { pushState } from '$app/navigation';
	import { page } from '$app/state';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		ArrowDown01Icon,
		PreviousIcon,
		NextIcon,
		PlayIcon,
		PauseIcon,
		ShuffleIcon,
		RepeatIcon,
		RepeatOne01Icon,
		FavouriteIcon,
		Queue01Icon,
		Mic01Icon,
		MusicNote01Icon
	} from '@hugeicons/core-free-icons';
	import * as api from '$lib/api';
	import { cycleRepeat, np, playback, toggleNowPlayingRating } from '$lib/player.svelte';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';
	import M3SeekWave from './M3SeekWave.svelte';
	import QueueList from './QueueList.svelte';
	import LyricsView from './LyricsView.svelte';

	let sheet = $state<'queue' | 'lyrics' | null>(null);
	let pushed = $state(false);

	onMount(() => {
		if (!page.state.player) pushState('', { player: true });
		pushed = true;
	});
	// Back (the button or the gesture) popped our entry: close.
	$effect(() => {
		if (pushed && !page.state.player) np.open = false;
	});
	$effect(() => {
		if (sheet && !page.state.sheet) sheet = null;
	});
	function openSheet(which: 'queue' | 'lyrics') {
		sheet = which;
		if (!page.state.sheet) pushState('', { player: true, sheet: true });
	}
	function closeSheet() {
		if (page.state.sheet) history.back();
		else sheet = null;
	}
	function close() {
		if (page.state.player) history.back();
		else np.open = false;
	}

	const fmt = (secs: number) => {
		if (!secs || secs < 0) return '0:00';
		const s = Math.floor(secs);
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		const mm = h ? m.toString().padStart(2, '0') : `${m}`;
		return `${h ? `${h}:` : ''}${mm}:${(s % 60).toString().padStart(2, '0')}`;
	};

	// Same seek handling as the player bar: hold the dragged value so position ticks can't yank the
	// thumb back, and only seek on release. Shown at the selected tempo; mpv seeks in track seconds.
	let seekDrag = $state<number | null>(null);
	const shownPosition = $derived((seekDrag ?? playback.position) / playback.speed);
	const shownDuration = $derived(playback.duration / playback.speed);
	const seekPct = $derived(
		shownDuration > 0 ? Math.min(1, Math.max(0, shownPosition / shownDuration)) : 0
	);
	function onSeekInput(e: Event) {
		seekDrag = Number((e.target as HTMLInputElement).value) * playback.speed;
	}
	function onSeekCommit(e: Event) {
		const v = Number((e.target as HTMLInputElement).value) * playback.speed;
		playback.position = v;
		seekDrag = null;
		api.seek(v);
	}

	const shuffleOn = $derived(playback.queue.shuffle ?? false);
	const repeat = $derived(playback.queue.repeat ?? 'off');
	const liked = $derived(playback.rating === 'like');
	const canRate = $derived(!!playback.now && !api.isLocalId(playback.now.videoId));
</script>

{#if playback.now}
	<div
		data-mobile-player
		class="fixed inset-0 z-40 flex flex-col overflow-hidden bg-background"
		transition:fly={{ y: 48, duration: 240, easing: cubicOut }}
	>
		<!-- The cover, blurred, as the backdrop. -->
		{#if playback.now.thumbnail}
			<img
				src={thumb(playback.now.thumbnail, 120)}
				alt=""
				aria-hidden="true"
				class="pointer-events-none absolute inset-0 -z-10 h-full w-full scale-125 object-cover opacity-40 blur-3xl"
			/>
			<div class="pointer-events-none absolute inset-0 -z-10 bg-gradient-to-b from-background/30 via-background/70 to-background"></div>
		{/if}

		<header class="flex items-center gap-2 px-2 pt-2">
			<button
				type="button"
				class="flex h-11 w-11 items-center justify-center rounded-full"
				onclick={close}
				aria-label={t('player.minimize_player')}
			>
				<HugeiconsIcon icon={ArrowDown01Icon} class="h-6 w-6" />
			</button>
			<div class="min-w-0 flex-1 text-center">
				{#if playback.queue.sourceName}
					<div class="text-[11px] uppercase tracking-wide text-muted-foreground">{t('player.playing_from')}</div>
					<div class="truncate text-sm font-semibold">{playback.queue.sourceName}</div>
				{:else}
					<div class="text-sm font-semibold">{t('player.now_playing')}</div>
				{/if}
			</div>
			<div class="h-11 w-11"></div>
		</header>

		<div class="flex min-h-0 flex-1 flex-col justify-center gap-6 px-6 pb-4">
			<div class="mx-auto aspect-square w-full max-w-[min(85vw,52vh)]">
				{#key playback.now.videoId}
					{#if playback.now.thumbnail}
						<img
							src={thumb(playback.now.thumbnail, 720)}
							alt=""
							class="h-full w-full rounded-2xl object-cover shadow-2xl"
							in:fade={{ duration: 250 }}
						/>
					{:else}
						<div class="flex h-full w-full items-center justify-center rounded-2xl bg-muted text-muted-foreground/50">
							<HugeiconsIcon icon={MusicNote01Icon} class="h-16 w-16" />
						</div>
					{/if}
				{/key}
			</div>

			<div class="flex items-center gap-3">
				<div class="min-w-0 flex-1">
					<h2 class="truncate font-heading text-xl font-bold">{playback.now.title}</h2>
					<p class="truncate text-sm text-muted-foreground">{playback.now.artists}</p>
				</div>
				{#if canRate}
					<button
						type="button"
						class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full {liked ? 'text-primary' : 'text-muted-foreground'}"
						onclick={() => toggleNowPlayingRating()}
						aria-label={liked ? t('player.remove_from_liked') : t('player.save_to_liked')}
						aria-pressed={liked}
					>
						<HugeiconsIcon icon={FavouriteIcon} class="h-6 w-6 {liked ? 'fill-current' : ''}" />
					</button>
				{/if}
			</div>

			<div>
				<div class="m3-seek relative flex items-center" style="--pct:{seekPct * 100}%">
					<M3SeekWave pct={seekPct} paused={playback.paused} duration={shownDuration} dragging={seekDrag !== null} />
					<input
						type="range"
						class="range w-full flex-1"
						style="--pct:{seekPct * 100}%"
						min="0"
						max={shownDuration || 0}
						value={shownPosition}
						oninput={onSeekInput}
						onchange={onSeekCommit}
						aria-label={t('player.seek')}
					/>
				</div>
				<div class="mt-1 flex justify-between text-xs tabular-nums text-muted-foreground">
					<span>{fmt(shownPosition)}</span>
					<span>{fmt(shownDuration)}</span>
				</div>
			</div>

			<div class="flex items-center justify-between">
				<button
					type="button"
					class="flex h-12 w-12 items-center justify-center rounded-full {shuffleOn ? 'text-primary' : 'text-muted-foreground'}"
					onclick={() => api.toggleShuffle()}
					aria-label={t('player.shuffle')}
					aria-pressed={shuffleOn}
				>
					<HugeiconsIcon icon={ShuffleIcon} class="h-6 w-6" />
				</button>
				<button
					type="button"
					class="flex h-14 w-14 items-center justify-center rounded-full"
					onclick={() => api.prevTrack()}
					aria-label={t('player.previous')}
				>
					<HugeiconsIcon icon={PreviousIcon} class="h-8 w-8" />
				</button>
				<button
					type="button"
					class="flex h-18 w-18 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg"
					onclick={() => api.togglePause()}
					aria-label={playback.paused ? t('player.play') : t('player.pause')}
				>
					<!-- HugeiconsIcon freezes `icon` at mount: toggle with altIcon/showAlt, as PlayerBar does. -->
					<HugeiconsIcon icon={PauseIcon} altIcon={PlayIcon} showAlt={playback.paused} class="h-8 w-8" />
				</button>
				<button
					type="button"
					class="flex h-14 w-14 items-center justify-center rounded-full"
					onclick={() => api.nextTrack()}
					aria-label={t('player.next')}
				>
					<HugeiconsIcon icon={NextIcon} class="h-8 w-8" />
				</button>
				<button
					type="button"
					class="flex h-12 w-12 items-center justify-center rounded-full {repeat !== 'off' ? 'text-primary' : 'text-muted-foreground'}"
					onclick={cycleRepeat}
					aria-label={t(repeat === 'one' ? 'player.repeat_one' : repeat === 'all' ? 'player.repeat_all' : 'player.repeat_off')}
				>
					<HugeiconsIcon icon={RepeatIcon} altIcon={RepeatOne01Icon} showAlt={repeat === 'one'} class="h-6 w-6" />
				</button>
			</div>

			<div class="flex justify-center gap-3">
				<button
					type="button"
					class="flex items-center gap-2 rounded-full bg-card/70 px-5 py-2.5 text-sm font-medium"
					onclick={() => openSheet('lyrics')}
				>
					<HugeiconsIcon icon={Mic01Icon} class="h-4 w-4" />
					{t('player.lyrics')}
				</button>
				<button
					type="button"
					class="flex items-center gap-2 rounded-full bg-card/70 px-5 py-2.5 text-sm font-medium"
					onclick={() => openSheet('queue')}
				>
					<HugeiconsIcon icon={Queue01Icon} class="h-4 w-4" />
					{t('player.queue')}
				</button>
			</div>
		</div>

		{#if sheet}
			<button
				class="absolute inset-0 z-10 bg-black/50"
				onclick={closeSheet}
				aria-label={t('a11y.close')}
				transition:fade={{ duration: 150 }}
			></button>
			<section
				class="absolute inset-x-0 bottom-0 z-20 flex h-[75%] flex-col rounded-t-3xl border-t bg-background shadow-2xl"
				transition:fly={{ y: 200, duration: 240, easing: cubicOut }}
			>
				<div class="flex items-center justify-between px-5 pb-2 pt-3">
					<h3 class="font-heading text-base font-semibold">
						{sheet === 'queue' ? t('queue.title') : t('lyrics.title')}
					</h3>
					<button
						type="button"
						class="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground"
						onclick={closeSheet}
						aria-label={t('a11y.close')}
					>
						<HugeiconsIcon icon={ArrowDown01Icon} class="h-5 w-5" />
					</button>
				</div>
				<div class="flex min-h-0 flex-1 flex-col">
					{#if sheet === 'queue'}<QueueList />{:else}<LyricsView />{/if}
				</div>
			</section>
		{/if}
	</div>
{/if}
