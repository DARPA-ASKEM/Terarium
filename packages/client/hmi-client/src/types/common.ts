import { XDDFacetsItemResponse, Document, Dataset, Model } from '@/types/Types';
import { ConceptFacets } from './Concept';
import { DatasetSearchParams } from './Dataset';
import { ModelSearchParams } from './Model';
import { XDDSearchParams } from './XDD';
import { ProjectAssetTypes, ProjectPages } from './Project';

export type Annotation = {
	id: string;
	artifact_id: string;
	artifact_type: string;
	content: string;
	timestampMillis: number;
	username: number;
	section: string;
};

// TODO: Wherever these are used - investigate using an actual map instead, this has been avoided due to v-model not playing well with maps
// But a solution might be found here: https://stackoverflow.com/questions/37130105/does-vue-support-reactivity-on-map-and-set-data-types/64512468#64512468
export interface StringValueMap {
	[key: string]: string;
}

export interface NumericValueMap {
	[key: string]: number;
}

export interface AnyValueMap {
	[key: string]: any;
}

export enum ViewType {
	LIST = 'list',
	MATRIX = 'matrix',
	GRAPH = 'graph'
}

export enum ResourceType {
	XDD = 'xdd',
	MODEL = 'model',
	DATASET = 'dataset',
	ALL = 'all'
}

export type SearchParameters = {
	[ResourceType.XDD]?: XDDSearchParams;
	[ResourceType.MODEL]?: ModelSearchParams;
	[ResourceType.DATASET]?: DatasetSearchParams;
};

export type ResultType = Model | Dataset | Document;

export type SearchResults = {
	results: ResultType[];
	facets?: { [p: string]: XDDFacetsItemResponse } | Facets;
	rawConceptFacets?: ConceptFacets | null;
	searchSubsystem: string;
	hits?: number;
	hasMore?: boolean;
	nextPage?: string;
};

export type FullSearchResults = {
	allData: SearchResults;
	allDataFilteredWithFacets: SearchResults;
};

export type SearchByExampleOptions = {
	similarContent: boolean;
	forwardCitation: boolean;
	backwardCitation: boolean;
	relatedContent: boolean;
};

//
// Facets
//
export type FacetBucket = {
	key: string;
	value: number;
};

export type Facets = {
	[key: string]: FacetBucket[];
};

// Side panel
export type SidePanelTab = {
	name: string;
	icon?: string;
	imgSrc?: string;
	isGreyscale?: string;
	badgeCount?: number;
};

// Tabs
export type Tab = {
	assetName: string;
	icon?: string;
	assetId?: string;
	pageType?: ProjectAssetTypes | ProjectPages;
};

export type CodeRequest = {
	asset: Tab;
	code?: string;
};

// TODO this should come from the back end, and we should also have maps for the "categories" of types (artifacts, models, datasets, etc)
export enum AcceptedTypes {
	PDF = 'application/pdf',
	CSV = 'text/csv',
	TXT = 'text/plain',
	MD = 'text/markdown',
	PY = 'text/x-python-script',
	M = 'text/x-matlab',
	JS = 'application/javascript',
	R = 'text/x-r'
}

export enum AcceptedExtensions {
	PDF = 'pdf',
	CSV = 'csv',
	TXT = 'txt',
	MD = 'md',
	PY = 'py',
	M = 'm',
	JS = 'js',
	R = 'r'
}

export interface PDFExtractionResponseType {
	text: string;
	images: string[];
}
