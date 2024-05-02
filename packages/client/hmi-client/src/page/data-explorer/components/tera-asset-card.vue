<template>
	<div
		class="asset-card"
		draggable="true"
		@dragstart="startDrag(asset, resourceType)"
		@dragend="endDrag"
	>
		<main>
			<div class="type-and-filters">
				{{ resourceType !== ResourceType.XDD ? resourceType.toUpperCase() : 'DOCUMENT' }}
				<div
					class="asset-filters"
					v-if="
						resourceType === ResourceType.XDD &&
						source === DocumentSource.XDD &&
						(asset as Document).knownEntities?.askemObjects
					"
				>
					<template
						v-for="icon in [
							{ type: XDDExtractionType.URL, class: 'pi-link' },
							{ type: XDDExtractionType.Figure, class: 'pi-chart-bar' },
							{ type: XDDExtractionType.Table, class: 'pi-table' },
							{ type: XDDExtractionType.Doc, class: 'pi-file-pdf' }
						]"
						:key="icon"
					>
						<i
							:class="`pi ${icon.class}`"
							:active="chosenExtractionFilter === icon.type"
							@click.stop="updateExtractionFilter(icon.type)"
						/>
					</template>
				</div>
				<div v-if="resourceType === ResourceType.MODEL">
					{{ (asset as Model).header.schema_name }}
				</div>
				<ul>
					<li v-for="(project, index) in foundInProjects" :key="index">
						<a>{{ project }}</a>
					</li>
				</ul>
			</div>
			<header class="title" v-html="title" />
			<div class="details" v-html="formatDetails" />
			<ul class="snippets" v-if="snippets">
				<li v-for="(snippet, index) in snippets" :key="index" v-html="snippet" />
			</ul>
			<div
				class="description"
				v-if="resourceType === ResourceType.MODEL"
				v-html="highlightSearchTerms((asset as Model).header.description)"
			/>
			<div
				class="description"
				v-else-if="resourceType === ResourceType.DATASET"
				v-html="highlightSearchTerms((asset as Dataset).description)"
			/>
			<div
				class="description"
				v-else-if="resourceType === ResourceType.XDD"
				v-html="highlightSearchTerms((asset as DocumentAsset).description)"
			/>
			<div
				class="parameters"
				v-if="
					resourceType === ResourceType.MODEL &&
					(asset as Model).semantics?.ode?.parameters
				"
			>
				PARAMETERS:
				{{ (asset as Model).semantics?.ode.parameters }}
				<!--may need a formatting function this attribute is always undefined at the moment-->
			</div>
			<div class="features" v-else-if="resourceType === ResourceType.DATASET">
				FEATURES:
				<span v-for="(feature, index) in formatFeatures()" :key="index">
					{{ feature }},
				</span>
			</div>
			<footer><!--pill tags if already in another project--></footer>
		</main>
		<aside>
			<tera-carousel
				v-if="
					resourceType === ResourceType.XDD &&
					source === DocumentSource.XDD &&
					!isEmpty(extractions)
				"
				is-numeric
				height="6rem"
				width="8rem"
			>
				<template v-for="(extraction, index) in extractions">
					<img
						v-if="extraction.properties.image"
						:src="`data:image/jpeg;base64,${extraction.properties.image}`"
						class="extracted-assets"
						alt="asset"
						:key="index"
					/>
					<a
						v-else-if="
							extraction.properties.doi && extraction.properties.documentBibjson?.link
						"
						:href="extraction.properties.documentBibjson.link[0].url"
						@click.stop
						rel="noreferrer noopener"
						:key="`${index}a`"
					>
						{{ extraction.properties.documentBibjson.link[0].url }}
					</a>
					<a
						v-else-if="extraction.properties.doi"
						:href="`https://doi.org/${extraction.properties.doi}`"
						@click.stop
						rel="noreferrer noopener"
						:key="`${index}b`"
					>
						{{ `https://doi.org/${extraction.properties.doi}` }}
					</a>
					<a
						v-else-if="extraction.urlExtraction"
						:href="extraction.urlExtraction.url"
						@click.stop
						rel="noreferrer noopener"
						:key="`${index}c`"
					>
						{{ extraction.urlExtraction.resourceTitle }}
					</a>
				</template>
			</tera-carousel>
			<slot name="default"></slot>
		</aside>
	</div>
</template>

<script setup lang="ts">
import TeraCarousel from '@/components/widgets/tera-carousel.vue';
import { useDragEvent } from '@/services/drag-drop';
import type {
	Dataset,
	Document,
	DocumentAsset,
	Extraction,
	Model,
	XDDUrlExtraction
} from '@/types/Types';
import { XDDExtractionType } from '@/types/XDD';
import { ResourceType, ResultType } from '@/types/common';
import { DocumentSource } from '@/types/search';
import * as textUtil from '@/utils/text';
import { isEmpty } from 'lodash';
import { ComputedRef, computed, ref, watch } from 'vue';

// This type is for easy frontend integration with the rest of the extraction types (just for use here)
type UrlExtraction = {
	askemClass: XDDExtractionType;
	urlExtraction: XDDUrlExtraction;
};

const props = defineProps<{
	asset: ResultType;
	resourceType: ResourceType;
	source: string;
	highlight?: string;
}>();

// Highlight strings based on props.highlight
function highlightSearchTerms(text: string | undefined): string {
	if (!!props.highlight && !!text) {
		return textUtil.highlight(text, props.highlight);
	}
	return text ?? '';
}

const relatedAssetPage = ref<number>(0);
const chosenExtractionFilter = ref<XDDExtractionType | 'Asset'>('Asset');

const foundInProjects = computed(() => [] /* ['project 1', 'project 2'] */);

const urlExtractions = computed(() => {
	const urls: UrlExtraction[] = [];

	if ((props.asset as Document).knownEntities.askemObjects) {
		const documentsWithUrls = (props.asset as Document).knownEntities.askemObjects.filter(
			(ex) =>
				ex.askemClass === XDDExtractionType.Doc &&
				ex.properties.documentBibjson?.knownEntities &&
				!isEmpty(ex.properties.documentBibjson.knownEntities.urlExtractions)
		);

		for (let i = 0; i < documentsWithUrls.length; i++) {
			const knownEntities = documentsWithUrls[i].properties.documentBibjson.knownEntities;

			if (knownEntities) {
				for (let j = 0; i < knownEntities.urlExtractions.length; j++) {
					urls.push({
						askemClass: XDDExtractionType.URL,
						urlExtraction: knownEntities.urlExtractions[j]
					});
				}
			}
		}
	}
	return urls;
});

const extractions: ComputedRef<UrlExtraction[] & Extraction[]> = computed(() => {
	if ((props.asset as Document).knownEntities.askemObjects) {
		const allExtractions = [
			...((props.asset as Document).knownEntities.askemObjects as UrlExtraction[] &
				Extraction[]),
			...(urlExtractions.value as UrlExtraction[] & Extraction[])
		];

		if (chosenExtractionFilter.value === 'Asset') return allExtractions;

		return allExtractions.filter((ex) => ex.askemClass === chosenExtractionFilter.value);
	}
	return [];
});

const snippets = computed(() =>
	(props.asset as Document).highlight
		? Array.from((props.asset as Document).highlight).splice(0, 3)
		: null
);
const title = computed(() => {
	let value = '';
	if (props.resourceType === ResourceType.XDD) {
		value =
			props.source === DocumentSource.XDD
				? (props.asset as Document).title
				: (props.asset as DocumentAsset).name ?? '';
	} else if (props.resourceType === ResourceType.MODEL) {
		value = (props.asset as Model).header.name;
	} else if (props.resourceType === ResourceType.DATASET) {
		value = (props.asset as Dataset).name ?? '';
	}
	return highlightSearchTerms(value);
});

// Reset page number on new search and when chosenExtractionFilter is changed
watch(
	() => [props.asset, chosenExtractionFilter.value],
	() => {
		relatedAssetPage.value = 0;
	}
);

function updateExtractionFilter(extractionType: XDDExtractionType) {
	chosenExtractionFilter.value =
		chosenExtractionFilter.value === extractionType ? 'Asset' : extractionType;
}

//
// in case we decided to display matching concepts associated with artifacts
//  when performing a search using a keyword that represents a known concept
//
// const getConceptTags = (item: ResultType) => {
// 	const tags = [] as string[];
// 	if (props.rawConceptFacets) {
// 		const itemConcepts = props.rawConceptFacets.results.filter(
// 			(conceptResult) => conceptResult.id === item.id
// 		);
// 		tags.push(...itemConcepts.map((c) => c.name ?? c.curie));
// 	}
// 	return tags;
// };

// Return formatted author, year, journal
// Return formatted author, year, journal
const formatDetails = computed(() => {
	if (props.resourceType === ResourceType.XDD && props.source === DocumentSource.XDD) {
		const details = `${(props.asset as Document).author.map((a) => a.name).join(', ')} (${
			(props.asset as Document).year
		}) ${(props.asset as Document).journal}`;
		return highlightSearchTerms(details);
	}

	if (props.resourceType === ResourceType.DATASET) {
		return (props.asset as Dataset).datasetUrls;
	}

	return null;
});

// Format features for dataset type
const formatFeatures = () => {
	if (props.resourceType === ResourceType.DATASET) {
		// TODO: Once we have enrichment and extraction working we can see what annotations will look like
		/* const columns = props.asset.columns ?? [];
		if (!columns || columns.length === 0) return [];
		const annotations = columns.map((c) => (c.annotations ? c.annotations : []));
		const annotationNames = annotations.map((a) => (a.name ? a.name : ''));
		const max = 5;
		return annotationNames.length < max ? annotationNames : annotationNames.slice(0, max); */
	}
	return [];
};

const { setDragData, deleteDragData } = useDragEvent();

function startDrag(asset, resourceType) {
	setDragData('asset', asset);
	setDragData('resourceType', resourceType);
}

function endDrag() {
	deleteDragData('asset');
	deleteDragData('resourceType');
}
</script>

<style scoped>
.asset-card {
	background-color: var(--surface-a);
	border: 1px solid var(--surface-border-light);
	border-radius: var(--border-radius);
	color: var(--text-color-subdued);
	display: flex;
	font-size: var(--font-caption);
	justify-content: space-between;
	margin: 1px;
	min-height: 5rem;
	padding: 0.5rem 0.625rem 0.625rem;

	& > main {
		width: 100%;
	}
}

.asset-card:hover {
	border-color: var(--surface-border);
	cursor: pointer;
}

.asset-card[active='true'] {
	outline: 1px solid var(--primary-color-dark);
}

.type-and-filters {
	display: flex;
	align-items: center;
	gap: 2rem;
	font-size: 0.75rem;

	& > ul {
		margin-left: auto;
		list-style: none;
		display: flex;
		margin-right: 0.5rem;
		gap: 0.5rem;

		/* & > li:not(:last-child)::after {
			content: '-';
		} */
	}
}

aside {
	display: flex;
	gap: 0.5rem;
}

.title,
.details,
.description,
.parameters,
.features,
.snippets li {
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 1;
	overflow: hidden;
}

.asset-filters {
	display: flex;
	gap: 0.5rem;
}

.title {
	color: var(--text-color-primary);
	font-size: var(--font-body-medium);
	margin: 0.1rem 0 0.1rem 0;
}

.details {
	margin: 0rem 0 0.25rem 0;
	font-size: var(--font-size);
}

i {
	padding: 0.2rem;
	border-radius: 3px;
	font-size: var(--font-caption);
}

.pi[active='true'] {
	background-color: var(--primary-color-light);
}

i:hover {
	cursor: pointer;
	background-color: hsla(0, 0%, 0%, 0.1);
}

.snippets {
	list-style: none;
}

/* Add ellipsis around a snippet */
.snippets li::before,
.snippets li::after {
	content: '…';
}
</style>
