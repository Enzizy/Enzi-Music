<script lang="ts">
	// A hover label for an icon-only control. `trigger` renders the control itself with the props
	// it is handed, so the tooltip wraps our own Button instead of adding an element around it.
	import type { Snippet } from 'svelte';
	import * as Tooltip from '$lib/components/ui/tooltip';

	let {
		label,
		side = 'top',
		trigger
	}: {
		label: string;
		side?: 'top' | 'bottom' | 'left' | 'right';
		trigger: Snippet<[Record<string, unknown>]>;
	} = $props();
</script>

<Tooltip.Provider delayDuration={250}>
	<Tooltip.Root>
		<Tooltip.Trigger>
			{#snippet child({ props })}
				{@render trigger(props)}
			{/snippet}
		</Tooltip.Trigger>
		<Tooltip.Content {side} sideOffset={6}>{label}</Tooltip.Content>
	</Tooltip.Root>
</Tooltip.Provider>
