import { cloneDeep, uniq, uniqBy } from 'lodash';
import {
	Facets,
	FullSearchResults,
	ResourceType,
	SearchParameters,
	SearchResults
} from '@/types/common';
import API from '@/api/api';
import { getDatasetFacets, getModelFacets } from '@/utils/facets';
import { applyFacetFilters } from '@/utils/data-util';
import { ConceptFacets, CONCEPT_FACETS_FIELD } from '@/types/Concept';
import { ProjectAssetTypes } from '@/types/Project';
import { Clause, ClauseValue } from '@/types/Filter';
import { Dataset, DatasetSearchParams, DATASET_FILTER_FIELDS } from '@/types/Dataset';
import { ID, Model, ModelSearchParams, MODEL_FILTER_FIELDS } from '../types/Model';
import {
	XDDArticle,
	XDDArtifact,
	XDDDictionary,
	XDDResult,
	XDDSearchParams,
	XDD_RESULT_DEFAULT_PAGE_SIZE
} from '../types/XDD';
import { getFacets as getConceptFacets } from './concept';
import * as DatasetService from './dataset';
import { getAllModelDescriptions } from './model';

const getXDDSets = async () => {
	const res = await API.get('/xdd/sets');
	const response: XDDResult = res.data;
	return response.available_sets || ([] as string[]);
};

const getXDDDictionaries = async () => {
	const res = await API.get('/xdd/dictionaries');
	const rawdata: XDDResult = res.data;
	if (rawdata.success) {
		const { data } = rawdata.success;
		return data;
	}
	return [] as XDDDictionary[];
};

const filterAssets = <T extends Model | Dataset>(
	allAssets: T[],
	resourceType: ResourceType,
	conceptFacets: ConceptFacets | null,
	term: string
) => {
	if (term.length > 0) {
		// simulate applying filters
		const AssetFilterAttributes: string[] =
			resourceType === ResourceType.MODEL ? MODEL_FILTER_FIELDS : DATASET_FILTER_FIELDS; // maybe turn into switch case when other resource types have to go through here

		let finalAssets: T[] = [];

		AssetFilterAttributes.forEach((attribute) => {
			finalAssets = allAssets.filter((d) =>
				(d[attribute as keyof T] as string).toLowerCase().includes(term.toLowerCase())
			);
		});

		// if no assets match keyword search considering the AssetFilterAttributes
		// perhaps the keyword search match a concept name, so let's also search for that
		if (conceptFacets) {
			const matchingCuries = [] as string[];
			Object.keys(conceptFacets.facets.concepts).forEach((curie) => {
				const concept = conceptFacets?.facets.concepts[curie];
				if (concept?.name?.toLowerCase() === term.toLowerCase()) {
					matchingCuries.push(curie);
				}
			});
			matchingCuries.forEach((curie) => {
				const matchingResult = conceptFacets?.results.filter((r) => r.curie === curie);
				const assetIDs = matchingResult?.map((mr) => mr.id);

				assetIDs?.forEach((assetId) => {
					const asset = allAssets.find((m) => m.id === assetId);
					if (asset) finalAssets.push(asset);
				});
			});
		}
		return uniqBy(finalAssets, ID);
	}
	return allAssets;
};

const getAssets = async (
	term: string,
	resourceType: ResourceType,
	searchParam?: ModelSearchParams | DatasetSearchParams
) => {
	const results = {} as FullSearchResults;

	// fetch list of model or datasets data from the HMI server
	let assetList: Model[] | Dataset[] = [];
	let projectAssetType: ProjectAssetTypes;

	switch (resourceType) {
		case ResourceType.MODEL:
			assetList = (await getAllModelDescriptions()) || ([] as Model[]);
			projectAssetType = ProjectAssetTypes.MODELS;
			break;
		case ResourceType.DATASET:
			assetList = (await DatasetService.getAll()) || ([] as Dataset[]);
			projectAssetType = ProjectAssetTypes.DATASETS;
			break;
		default:
			return results; // error or make new resource type compatible
	}

	// TEMP: add "type" field because it is needed to mark these resources as models or datasets
	// FIXME: dependency on type model should be removed and another "sub-system" or "result-type"
	//        should be added for datasets and other resource types
	const allAssets = assetList.map((a) => ({
		...a,
		temporalResolution: a?.temporal_resolution, // Dataset attribute
		geospatialResolution: a?.geospatial_resolution, // Dataset attribute
		simulationRun: a?.simulation_run, // Dataset attribute
		type: resourceType
	}));

	// first get un-filtered concept facets
	let conceptFacets = await getConceptFacets([projectAssetType]);

	// FIXME: this client-side computation of facets from "models" data should be done
	//        at the HMI server
	//
	// This is going to calculate facets aggregations from the list of results
	let assetResults = filterAssets(allAssets, resourceType, conceptFacets, term);
	let assetFacets: Facets;

	switch (resourceType) {
		case ResourceType.MODEL:
			assetResults = assetResults as Model[];
			assetFacets = getModelFacets(assetResults, conceptFacets); // will be moved to HMI server - keep this for now
			break;
		case ResourceType.DATASET:
			assetResults = assetResults as Dataset[];
			assetFacets = getDatasetFacets(assetResults, conceptFacets); // will be moved to HMI server - keep this for now
			break;
		default:
			return results; // error or make new resource type compatible
	}

	results.allData = {
		results: assetResults,
		searchSubsystem: resourceType,
		facets: assetFacets,
		rawConceptFacets: conceptFacets
	};

	// apply facet filters
	if (searchParam && searchParam.filters && searchParam.filters.clauses.length > 0) {
		// modelSearchParam currently represent facets filters that can be applied
		//  to further refine the list of models

		// a special facet related to ontology/DKG concepts needs to be transformed into
		//  some form of filters that can filter the list of models.
		// In this case, each concept has an associated list of model IDs that can be used to filter models
		//  so, we need to map the facet filters from field "concepts" to "id"

		// Each clause of 'concepts' should have another corresponding one with 'id'
		const curies = [] as ClauseValue[];
		const idClauses = [] as Clause[];
		searchParam.filters.clauses.forEach((clause) => {
			if (clause.field === CONCEPT_FACETS_FIELD) {
				const idClause = cloneDeep(clause);
				idClause.field = 'id';
				const clauseValues = [] as ClauseValue[];
				idClause.values.forEach((conceptNameOrCurie) => {
					// find the corresponding model IDs
					if (conceptFacets !== null) {
						const matching = conceptFacets.results.filter(
							(conceptResult) =>
								conceptResult.name === conceptNameOrCurie ||
								conceptResult.curie === conceptNameOrCurie
						);
						// update the clause value by mapping concept/curie to model id
						clauseValues.push(...matching.map((m) => m.id));
						curies.push(...matching.map((m) => m.curie));
					}
				});
				idClause.values = clauseValues;
				idClauses.push(idClause);
			}
		});
		// NOTE that we need to merge all concept filters into a single ID filter
		if (idClauses.length > 0) {
			const finalIdClause = cloneDeep(idClauses[0]);
			const allIdValues = idClauses.map((c) => c.values).flat();
			finalIdClause.values = uniq(allIdValues);
			searchParam.filters.clauses.push(finalIdClause);
		}

		applyFacetFilters(assetResults, searchParam.filters, resourceType);

		// remove any previously added concept/id filters
		searchParam.filters.clauses = searchParam.filters.clauses.filter((c) => c.field !== ID);

		// ensure that concepts are re-created following the current filtered list of model results
		// e.g., if the user has applied other facet filters, e.g. selected some model by name
		// then we need to find corresponding curies to filter the concepts accordingly
		if (conceptFacets !== null) {
			// FIXME:
			// This step won't be needed if the concept facets API is able to receive filters as well
			// to only provide concept aggregations based on a filtered set of models rather than the full list of models
			const finalAssetIDs = assetResults.map((m) => m.id);
			conceptFacets.results.forEach((conceptFacetResult) => {
				if (finalAssetIDs.includes(conceptFacetResult.id)) {
					curies.push(conceptFacetResult.curie);
				}
			});
		}

		// re-create the concept facets if the user has applyied any concept filters
		const uniqueCuries = uniq(curies);
		if (uniqueCuries.length > 0) {
			conceptFacets = await getConceptFacets([projectAssetType], uniqueCuries);
		}

		// FIXME: this client-side computation of facets from "models" data should be done
		//        at the HMI server
		//
		// This is going to calculate facets aggregations from the list of results
		let assetFacetsFiltered: Facets;
		switch (resourceType) {
			case ResourceType.MODEL:
				assetFacetsFiltered = getModelFacets(assetResults as Model[], conceptFacets);
				break;
			case ResourceType.DATASET:
				assetFacetsFiltered = getDatasetFacets(assetResults as Dataset[], conceptFacets);
				break;
			default:
				return results; // error or make new resource type compatible
		}

		results.allDataFilteredWithFacets = {
			results: assetResults,
			searchSubsystem: resourceType,
			facets: assetFacetsFiltered,
			rawConceptFacets: conceptFacets
		};
	} else {
		results.allDataFilteredWithFacets = results.allData;
	}

	return results;
};

//
// fetch list of extractions data from the HMI server
//
const getXDDArtifacts = async (doc_doi: string, term?: string) => {
	let url = '/xdd/extractions?';
	if (doc_doi !== '') {
		url += `doi=${doc_doi}`;
	}
	if (term !== undefined) {
		url += `query_all=${term}`;
	}

	const res = await API.get(url);
	const rawdata: XDDResult = res.data;

	if (rawdata.success) {
		const { data } = rawdata.success;
		const artifacts = data as XDDArtifact[];
		// TEMP: the following mapping is needed because the backend is returning raw xdd response
		return artifacts.map((a) => ({ ...a, askemClass: a.ASKEM_CLASS }));
	}

	return [] as XDDArtifact[];
};

//
// fetch list of related documented utilizing
//  semantic similarity (i.e., document embedding) from XDD via the HMI server
//
const getRelatedDocuments = async (docid: string, dataset: string | null) => {
	if (docid === '' || dataset) {
		return [] as XDDArticle[];
	}

	// https://xdd.wisc.edu/sets/xdd-covid-19/doc2vec/api/similar?doi=10.1002/pbc.28600
	// dataset=xdd-covid-19
	// doi=10.1002/pbc.28600
	// docid=5ebd1de8998e17af826e810e
	const url = `/xdd/related/document?docid=${docid}&set=${dataset}`;

	const res = await API.get(url);
	const rawdata: XDDResult = res.data;

	if (rawdata.data) {
		const articlesRaw = rawdata.data.map((a) => a.bibjson);

		// TEMP: since the backend has a bug related to applying mapping, the field "abstractText"
		//       is not populated and instead the raw field name, abstract, is the one with data
		//       similarly, re-map the gddid field
		const articles = articlesRaw.map((a) => ({
			...a,
			abstractText: a.abstract,
			// eslint-disable-next-line no-underscore-dangle
			gddid: a._gddid,
			knownTerms: a.known_terms
		}));

		return articles;
	}
	return [] as XDDArticle[];
};

const searchXDDArticles = async (term: string, xddSearchParam?: XDDSearchParams) => {
	const limitResultsCount = xddSearchParam?.perPage ?? XDD_RESULT_DEFAULT_PAGE_SIZE;

	// NOTE when true it disables ranking of results
	const enablePagination = xddSearchParam?.fullResults ?? false;

	// "full_results": "Optional. When this parameter is included (no value required),
	//  an overview of total number of matching articles is returned,
	//  with a scan-and-scroll cursor that allows client to step through all results page-by-page.
	//  NOTE: the "max" parameter will be ignored
	//  NOTE: results may not be ranked in this mode
	let searchParams = `term=${term}`;
	const url = '/xdd/documents?';

	if (xddSearchParam?.docid) {
		searchParams += `&docid=${xddSearchParam.docid}`;
	}
	if (xddSearchParam?.doi) {
		searchParams += `&doi=${xddSearchParam.doi}`;
	}
	if (xddSearchParam?.dataset) {
		searchParams += `&dataset=${xddSearchParam.dataset}`;
	}
	if (xddSearchParam?.fields) {
		searchParams += `&fields=${xddSearchParam.fields}`;
	}
	if (xddSearchParam?.dict && xddSearchParam?.dict.length > 0) {
		searchParams += `&dict=${xddSearchParam.dict.join(',')}`;
	}
	if (xddSearchParam?.min_published) {
		searchParams += `&min_published=${xddSearchParam.min_published}`;
	}
	if (xddSearchParam?.max_published) {
		searchParams += `&max_published=${xddSearchParam.max_published}`;
	}
	if (xddSearchParam?.pubname) {
		searchParams += `&pubname=${xddSearchParam.pubname}`;
	}
	if (xddSearchParam?.publisher) {
		searchParams += `&publisher=${xddSearchParam.publisher}`;
	}
	if (xddSearchParam?.includeHighlights) {
		searchParams += '&include_highlights=true';
	}
	if (xddSearchParam?.inclusive) {
		searchParams += '&inclusive=true';
	}
	if (enablePagination) {
		searchParams += '&full_results';
	} else {
		// request results to be ranked
		searchParams += '&include_score=true';
	}
	if (xddSearchParam?.facets) {
		searchParams += '&facets=true';
	}

	// search title and abstract when performing term-based search if requested
	if (term !== '' && xddSearchParam?.additional_fields) {
		searchParams += `&additional_fields=${xddSearchParam?.additional_fields}`;
	}

	// utilize ES improved matching
	if (term !== '' && xddSearchParam?.match) {
		searchParams += '&match=true';
	}

	if (xddSearchParam?.known_entities) {
		searchParams += `&known_entities=${xddSearchParam?.known_entities}`;
	}

	//
	// "max": "Maximum number of articles to return (default is all)",
	searchParams += `&max=${limitResultsCount}`;

	// "per_page": "Maximum number of results to include in one response.
	//  Applies to full_results pagination or single-page requests.
	//  NOTE: Due to internal mechanisms, actual number of results will be this parameter,
	//        floor rounded to a multiple of 25."
	searchParams += `&per_page=${limitResultsCount}`;

	// url = 'https://xdd.wisc.edu/api/articles?&include_score=true&max=25&term=abbott&publisher=USGS&full_results';

	// this will give error if "max" param is not included since the result is too large
	//  either set "max"
	//  or use the "full_results" which automatically sets a default of 500 per page (per_page)
	// url = 'https://xdd.wisc.edu/api/articles?dataset=xdd-covid-19&term=covid&include_score=true&full_results'

	const res = await API.get(url + searchParams);
	const rawdata: XDDResult = res.data;

	if (rawdata.success) {
		const { data, hits, scrollId, nextPage, facets } = rawdata.success;
		const articlesRaw =
			xddSearchParam?.fields === undefined
				? (data as XDDArticle[])
				: ((data as any).data as XDDArticle[]); // FIXME: xDD returns inconsistent resposne object

		// TEMP: since the backend has a bug related to applying mapping, the field "abstractText"
		//       is not populated and instead the raw field name, abstract, is the one with data
		//       similarly, re-map the gddid field
		// FIXME: setting the following mapping ignores the fact that the user may have specifically
		//        requested certain fields, and thus other mapped fields will be set to undefined
		const articles = articlesRaw.map((a) => ({
			...a,
			abstractText: a.abstract,
			// eslint-disable-next-line no-underscore-dangle
			gddid: a._gddid,
			knownTerms: a.known_terms,
			knownEntities: a.known_entities,
			// eslint-disable-next-line no-underscore-dangle
			highlight: a._highlight
		}));

		// process document highlights and style the search term differently in each highlight
		// FIXME: this styling of highlights with search term should be done automatically by XDD
		//        since the content is coming already styled and should not be done at the clinet side for performance reasons
		if (term !== '') {
			articles.forEach((article) => {
				if (article.highlight) {
					article.highlight = article.highlight.map((h) =>
						h.replaceAll(term, `<span style='background-color: yellow'>${term}</span>`)
					);
				}
			});
		}

		const formattedFacets: Facets = {};
		if (facets) {
			// we receive facets data, so make sure it is in the proper format
			const facetKeys = Object.keys(facets);
			facetKeys.forEach((facetKey) => {
				formattedFacets[facetKey] = facets[facetKey].buckets.map((e) => ({
					key: e.key,
					value: e.doc_count
				}));
			});
		}

		// also, perform search across extractions
		let extractionsSearchResults = [] as XDDArtifact[];
		if (term !== '') {
			extractionsSearchResults = await getXDDArtifacts('', term);
		}

		return {
			results: articles,
			facets: formattedFacets,
			xddExtractions: extractionsSearchResults,
			searchSubsystem: ResourceType.XDD,
			hits,
			hasMore: scrollId !== null && scrollId !== '',
			nextPage
		};
	}

	return {
		results: [] as XDDArticle[],
		searchSubsystem: ResourceType.XDD,
		hits: 0
	};
};

const getDocumentById = async (docid: string) => {
	const searchParams: XDDSearchParams = {
		docid,
		known_entities: 'url_extractions'
	};
	const xddRes = await searchXDDArticles('', searchParams);
	if (xddRes) {
		const articles = xddRes.results as XDDArticle[];
		if (articles.length > 0) {
			return articles[0];
		}
	}
	return null;
};

const fetchResource = async (
	term: string,
	searchParam?: SearchParameters,
	searchParamWithFacetFilters?: SearchParameters,
	resourceType?: string
): Promise<FullSearchResults> =>
	// eslint-disable-next-line no-async-promise-executor
	new Promise<FullSearchResults>(async (resolve, reject) => {
		try {
			switch (resourceType) {
				case ResourceType.XDD: // XDD
					resolve({
						allData: await searchXDDArticles(term, searchParam?.xdd),
						allDataFilteredWithFacets: await searchXDDArticles(
							term,
							searchParamWithFacetFilters?.xdd
						)
					});
					break;
				case ResourceType.MODEL: // Models
					resolve(getAssets(term, ResourceType.MODEL, searchParamWithFacetFilters?.model));
					break;
				case ResourceType.DATASET: // Datasets
					resolve(getAssets(term, ResourceType.DATASET, searchParamWithFacetFilters?.dataset));
					break;
				default:
					break;
			}
		} catch (err: any) {
			reject(new Error(`Error fetching ${resourceType} results: ${err}`));
		}
	});

const fetchData = async (
	term: string,
	searchParam?: SearchParameters,
	searchParamWithFacetFilters?: SearchParameters,
	resourceType?: string
) => {
	//
	// call the different search sub-systems to retrieve results
	// ideally, all such subsystems should be registered in an array, which will force refactoring of the following code
	//
	const promiseList = [] as Promise<FullSearchResults>[];

	if (resourceType) {
		if (resourceType === ResourceType.ALL) {
			Object.entries(ResourceType).forEach(async ([key]) => {
				if (ResourceType[key] !== ResourceType.ALL) {
					promiseList.push(
						fetchResource(term, searchParam, searchParamWithFacetFilters, ResourceType[key])
					);
				}
			});
		} else if ((<any>Object).values(ResourceType).includes(resourceType)) {
			promiseList.push(fetchResource(term, searchParam, searchParamWithFacetFilters, resourceType));
		}
	}

	// fetch results from all search subsystems in parallel
	const responses = await Promise.all(promiseList);

	const finalResponse = {} as {
		allData: SearchResults[];
		allDataFilteredWithFacets: SearchResults[];
	};
	finalResponse.allData = responses.map((r) => r.allData);
	finalResponse.allDataFilteredWithFacets = responses.map((r) => r.allDataFilteredWithFacets);
	return finalResponse;
};

export {
	fetchData,
	getXDDSets,
	getXDDDictionaries,
	getXDDArtifacts,
	searchXDDArticles,
	getAssets,
	getRelatedDocuments,
	getDocumentById
};
