<template>
	<footer>
		<img src="@assets/svg/uncharted-logo-dark.svg" alt="logo" />
		<nav>
			<a target="_blank" rel="noopener noreferrer" href="https://terarium.canny.io/report-an-issue">
				Report an issue
			</a>
			<a target="_blank" rel="noopener noreferrer" href="https://terarium.canny.io/request-a-feature">
				Request a feature
			</a>
		</nav>
	</footer>
</template>

<style scoped>
footer {
	align-items: center;
	background-color: var(--surface-section);
	border-top: 1px solid var(--surface-border-light);
	display: flex;
	gap: var(--gap-8);
	grid-area: footer;
	height: 2.5rem;
	justify-content: space-between;
}

img {
	margin-left: var(--gap-2);
	height: 2rem;
}

nav {
	align-items: center;
	display: flex;
	font-size: var(--font-caption);
	gap: var(--gap-8);
	justify-content: space-around;
	margin: 0 var(--gap-8);
}

a {
	text-decoration: none;
}

p {
	max-width: 40rem;
}
</style>
