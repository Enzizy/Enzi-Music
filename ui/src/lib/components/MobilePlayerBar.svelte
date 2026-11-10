<script lang="ts">
	// The phone's player strip, above the tabs: the track, play/pause and next. Tapping anywhere else
	// on it opens the full-screen player, which has the rest.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { NextIcon, PlayIcon, PauseIcon, MusicNote01Icon } from '@hugeicons/core-free-icons';
	import * as api from '$lib/api';
	import { np, playback } from '$lib/player.svelte';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';

	const pct = $derived(
		playback.duration > 0 ? Math.min(100, (playback.position / playback.duration) * 100) : 0
	);
</script>

{#if playback.now}
	<div data-player-bar class="relative shrink-0 border-t bg-card">
		<div class="absolute inset-x-0 top-0 h-0.5 bg-muted">
			<div class="h-full bg-primary" style="width:{pct}%"></div>
		</div>
		<div class="flex items-center gap-3 px-3 py-2">
			<button
				type="button"
				class="flex min-w-0 flex-1 items-center gap-3 text-left"
				onclick={() => (np.open = true)}
				aria-label={t('player.open_player')}
			>
				{#if playback.now.thumbnail}
					<img src={thumb(playback.now.thumbnail, 120)} alt="" class="h-11 w-11 shrink-0 rounded-md object-cover" />
				{:else}
					<span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground/60">
						<HugeiconsIcon icon={MusicNote01Icon} class="h-5 w-5" />
					</span>
				{/if}
				<span class="min-w-0">
					<span class="block truncate text-sm font-semibold">{playback.now.title}</span>
					<span class="block truncate text-xs text-muted-foreground">{playback.now.artists}</span>
				</span>
			</button>
			<button
				type="button"
				class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
				onclick={() => api.togglePause()}
				aria-label={playback.paused ? t('player.play') : t('player.pause')}
			>
				<HugeiconsIcon icon={playback.paused ? PlayIcon : PauseIcon} class="h-6 w-6" />
			</button>
			<button
				type="button"
				class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
				onclick={() => api.nextTrack()}
				aria-label={t('player.next')}
			>
				<HugeiconsIcon icon={NextIcon} class="h-6 w-6" />
			</button>
		</div>
	</div>
{/if}
