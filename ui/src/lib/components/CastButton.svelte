<script lang="ts">
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { CastIcon } from '@hugeicons/core-free-icons';
	import * as api from '$lib/api';
	import { t } from '$lib/i18n.svelte';
	import { playback, commitVolume } from '$lib/player.svelte';
	import { Button } from '$lib/components/ui/button';
	import * as Dialog from '$lib/components/ui/dialog';

	let { mobile = false }: { mobile?: boolean } = $props();
	let open = $state(false);
	let scanning = $state(false);
	let connecting = $state<api.CastTarget | null>(null);
	let disconnecting = $state(false);
	let devices = $state<api.CastTarget[]>([]);
	let error = $state('');

	async function scan() {
		if (scanning) return;
		scanning = true;
		error = '';
		devices = [];
		try {
			devices = await api.listCastDevices();
		} catch (cause) {
			error = String(cause);
		} finally {
			scanning = false;
		}
	}

	async function connect(device: api.CastTarget) {
		connecting = device;
		error = '';
		try {
			await api.connectCast(device.id);
		} catch (cause) {
			error = String(cause);
		} finally {
			connecting = null;
		}
	}

	async function disconnect() {
		disconnecting = true;
		error = '';
		try {
			await api.disconnectCast();
			open = false;
		} catch (cause) {
			error = String(cause);
		} finally {
			disconnecting = false;
		}
	}
</script>

<Button
	variant={playback.castTarget ? 'secondary' : 'ghost'}
	size={mobile ? 'icon' : 'icon-sm'}
	class={mobile ? 'h-11 w-11 rounded-full' : ''}
	title={playback.castTarget?.name ?? t('cast.title')}
	aria-label={t('cast.title')}
	onclick={(event) => {
		event.stopPropagation();
		open = true;
		if (!playback.castTarget && !connecting) void scan();
	}}
>
	<HugeiconsIcon icon={CastIcon} class="h-5 w-5" />
</Button>

<Dialog.Root bind:open>
	<Dialog.Content class="sm:max-w-sm">
		<Dialog.Header>
			<Dialog.Title>{t('cast.title')}</Dialog.Title>
			<Dialog.Description>
				{playback.castTarget
					? t('cast.connected', { device: playback.castTarget.name })
					: t('cast.choose')}
			</Dialog.Description>
		</Dialog.Header>
		{#if playback.castTarget}
			<label class="flex items-center gap-3 text-sm">
				{t('player.volume')}
				<input
					class="w-full accent-primary"
					type="range"
					min="0"
					max="100"
					value={playback.volume}
					onchange={(event) => commitVolume(event.currentTarget.valueAsNumber)}
				/>
			</label>
			<Button variant="outline" disabled={disconnecting} onclick={disconnect}>
				{t('cast.disconnect')}
			</Button>
		{:else if scanning}
			<p role="status" class="text-sm text-muted-foreground">{t('cast.searching')}</p>
		{:else}
			{#if connecting}
				<p role="status" class="text-sm text-muted-foreground">
					{t('cast.connecting', { device: connecting.name })}
				</p>
			{/if}
			{#if devices.length}
				<div class="flex max-h-56 flex-col gap-1 overflow-y-auto">
					{#each devices as device (device.id)}
						<Button variant="outline" class="justify-start" disabled={!!connecting} onclick={() => connect(device)}>
							{device.name}
						</Button>
					{/each}
				</div>
			{:else if !error}
				<p class="text-sm text-muted-foreground">{t('cast.empty')}</p>
			{/if}
			<Button variant="ghost" disabled={!!connecting} onclick={scan}>{t('cast.refresh')}</Button>
		{/if}
		{#if error}<p role="alert" class="text-sm text-destructive">{error}</p>{/if}
	</Dialog.Content>
</Dialog.Root>
