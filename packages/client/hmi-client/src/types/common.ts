import { ConceptFacets } from './Concept';
import { Dataset, DatasetSearchParams } from './Dataset';
import { Model, ModelSearchParams } from './Model';
import { XDDSearchParams } from './XDD';
import { XDDArtifact, DocumentType } from './Document';

export type Annotation = {
	artifact_id: string;
	artifact_type: string;
	content: string;
	timestampMillis: number;
	username: number;
};

export enum ViewType {
	LIST = 'list',
	MATRIX = 'matrix',
	GRAPH = 'graph'
}

export enum IAsset {
	DOCUMENT = 'document',
	MODEL = 'model',
	DATASET = 'dataset',
	INTERMEDIATE = 'intermediate',
	SIMULATION_PLAN = 'simulation_plan',
	SIMULATION_RUN = 'simulation_run',
	CODE = 'code',
	ALL = 'all'
}

export type SearchParameters = {
	[IAsset.DOCUMENT]?: XDDSearchParams;
	[IAsset.MODEL]?: ModelSearchParams;
	[IAsset.DATASET]?: DatasetSearchParams;
};

export type ResultType = Model | Dataset | DocumentType;

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
	assetId?: string | number;
	assetType?: IAsset;
};
