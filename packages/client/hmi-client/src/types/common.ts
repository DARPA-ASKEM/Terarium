import { ConceptFacets } from './Concept';
import { Dataset, DatasetSearchParams } from './Dataset';
import { Model, ModelSearchParams } from './Model';
import { XDDArticle, XDDArtifact, XDDSearchParams } from './XDD';

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

export type ResultType = Model | Dataset | XDDArticle;

export type SearchResults = {
	results: ResultType[];
	facets?: Facets;
	rawConceptFacets?: ConceptFacets | null;
	xddExtractions?: XDDArtifact[]; // the result from searching XDD artifacts against a given search term
	searchSubsystem: string;
	hits?: number;
	hasMore?: boolean;
	nextPage?: string;
};

export type FullSearchResults = {
	allData: SearchResults;
	allDataFilteredWithFacets: SearchResults;
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
	name: string;
	props?: Object;
};
