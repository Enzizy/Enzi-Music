<script lang="ts">
	// The phone's navigation: the sidebar's pages plus Settings, which is a dialog rather than a page.
	import { page } from '$app/state';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		Home01Icon,
		Search01Icon,
		LibraryIcon,
		Chart02Icon,
		Settings01Icon
	} from '@hugeicons/core-free-icons';
	import { np, ui } from '$lib/player.svelte';
	import { t } from '$lib/i18n.svelte';

	const tabs = $derived([
		{ href: '/', label: t('nav.home'), icon: Home01Icon },
		{ href: '/search', label: t('nav.search'), icon: Search01Icon },
		{ href: '/library', label: t('nav.library'), icon: LibraryIcon },
		{ href: '/stats', label: t('nav.stats'), icon: Chart02Icon }
	]);
	const isActive = (href: string) =>
		href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);
</script>

<nav data-bottom-nav class="flex shrink-0 items-stretch border-t bg-card">
	{#each tabs as tab (tab.href)}
		{@const active = isActive(tab.href)}
		<a
			href={tab.href}
			onclick={() => (np.open = false)}
			class="flex flex-1 flex-col items-center gap-1 pb-2 pt-2.5 text-[11px] font-medium transition-colors {active
				? 'text-foreground'
				: 'text-muted-foreground'}"
			aria-current={active ? 'page' : undefined}
		>
			<span class="flex h-7 w-14 items-center justify-center rounded-full {active ? 'bg-primary/20 text-primary' : ''}">
				<HugeiconsIcon icon={tab.icon} class="h-5 w-5" />
			</span>
			{tab.label}
		</a>
	{/each}
	<button
		type="button"
		onclick={() => (ui.settingsOpen = true)}
		class="flex flex-1 flex-col items-center gap-1 pb-2 pt-2.5 text-[11px] font-medium text-muted-foreground"
	>
		<span class="flex h-7 w-14 items-center justify-center rounded-full">
			<HugeiconsIcon icon={Settings01Icon} class="h-5 w-5" />
		</span>
		{t('nav.settings')}
	</button>
</nav>
