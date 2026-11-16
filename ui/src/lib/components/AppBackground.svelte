<script lang="ts">
	// The user's background picture (background.svelte.ts), under everything in the app shell. The
	// shell is `isolate` while it shows, so -z-10 lands above the shell's own fill and below its
	// content. A wash of the theme's background colour on top keeps text readable on any picture.
	import { background } from '$lib/background.svelte';
</script>

{#if background.src}
	<div aria-hidden="true" class="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
		<img
			src={background.src}
			alt=""
			class="h-full w-full object-cover"
			style:filter={background.blur ? `blur(${background.blur}px)` : undefined}
			style:transform={background.blur ? 'scale(1.08)' : undefined}
		/>
		<div class="absolute inset-0 bg-background" style:opacity={background.dim / 100}></div>
	</div>
{/if}
