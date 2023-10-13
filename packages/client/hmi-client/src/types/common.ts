import { XDDFacetsItemResponse, Document, Dataset, Model, AssetType } from '@/types/Types';
import { ConceptFacets } from './Concept';
import { DatasetSearchParams } from './Dataset';
import { ModelSearchParams } from './Model';
import { XDDSearchParams } from './XDD';
import { ProjectPages } from './Project';

export type Annotation = {
	id: string;
	artifact_id: string;
	artifact_type: string;
	content: string;
	timestampMillis: number;
	username: number;
	section: string;
};

export interface FeatureConfig {
	isPreview: boolean;
}

export enum ParamType {
	CONSTANT,
	DISTRIBUTION,
	TIME_SERIES
}

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

export type AssetRoute = {
	assetId: string;
	pageType: AssetType | ProjectPages;
};

export interface AssetItem extends AssetRoute {
	icon?: string;
	assetName?: string;
}

export type CodeRequest = {
	asset: AssetItem;
	code?: string;
};

// TODO this should come from the back end, and we should also have maps for the "categories" of types (artifacts, models, datasets, etc)
export enum AcceptedTypes {
	PDF = 'application/pdf',
	CSV = 'text/csv',
	TXT = 'text/plain',
	MD = 'text/markdown',
	PY = 'text/x-python-script',
	R = 'text/x-r',
	JL = 'application/julia'
}

export enum AcceptedExtensions {
	PDF = 'pdf',
	CSV = 'csv',
	TXT = 'txt',
	MD = 'md',
	PY = 'py',
	R = 'r',
	JL = 'jl'
}

export interface PDFExtractionResponseType {
	text: string;
	images: string[];
}
