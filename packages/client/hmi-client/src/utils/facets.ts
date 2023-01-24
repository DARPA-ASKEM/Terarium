import { Facets, SearchResults, FacetBucket, ResourceType } from '@/types/common';
import { ConceptFacets, CONCEPT_FACETS_FIELD } from '@/types/Concept';
import {
	Dataset,
	FACET_FIELDS as DATASET_FACET_FIELDS,
	DISPLAY_NAMES as DATASET_DISPLAY_NAMES
} from '@/types/Dataset';
import {
	Model,
	FACET_FIELDS as MODEL_FACET_FIELDS,
	DISPLAY_NAMES as MODEL_DISPLAY_NAMES,
	ID
} from '@/types/Model';
import { DISPLAY_NAMES as XDD_DISPLAY_NAMES } from '@/types/XDD';
import { groupBy, mergeWith, isArray } from 'lodash';

// FIXME: this client-side computation of facets from "models" data should be done //////////////////no point in editing//////////////////
//        at the HMI server
export const getModelFacets = (models: Model[], conceptFacets: ConceptFacets | null) => {
	// utility function for manually calculating facet aggregation from model results
	const aggField = (fieldName: string) => {
		const aggs: FacetBucket[] = [];
		const modelsMap = models.map((model) => model[fieldName as keyof Model]);
		const grouped = groupBy(modelsMap);
		Object.keys(grouped).forEach((gKey) => {
			if (gKey !== '') {
				aggs.push({ key: gKey, value: grouped[gKey].length });
			}
		});
		return aggs;
	};

	const facets = {} as Facets;

	// create facet for concepts
	if (conceptFacets) {
		// Note that creating facets for concepts should ensure align with the list of model results
		// Unfortunately, concept facets are always returned from the backend without taking into consideration any applied filters
		// As a result, we need to check and update the aggregates as necessary
		facets[CONCEPT_FACETS_FIELD] = [];
		const conceptKeys = Object.keys(conceptFacets.facets.concepts);
		conceptKeys.forEach((conceptKey) => {
			const concept = conceptFacets?.facets.concepts[conceptKey];
			facets[CONCEPT_FACETS_FIELD].push({ key: concept.name ?? conceptKey, value: concept.count });
		});
	}

	// create facets from specific model fields
	MODEL_FACET_FIELDS.forEach((field) => {
		// exclude model ID as a facet since it is created from mapping concepts
		if (field !== ID) {
			const facetForField = aggField(field);
			if (facetForField.length > 0) {
				facets[field] = facetForField;
			}
		}
	});

	return facets;
};

// FIXME: this client-side computation of facets from "datasets" data should be done //////////////////no point in editing//////////////////
//        at the HMI server
export const getDatasetFacets = (datasets: Dataset[], conceptFacets: ConceptFacets | null) => {
	// utility function for manually calculating facet aggregation from dataset results
	const aggField = (fieldName: string) => {
		const aggs: FacetBucket[] = [];
		const datasetsMap = datasets.map((model) => model[fieldName as keyof Dataset]);
		const grouped = groupBy(datasetsMap);
		Object.keys(grouped).forEach((gKey) => {
			if (gKey !== '') {
				aggs.push({ key: gKey, value: grouped[gKey].length });
			}
		});
		return aggs;
	};

	const facets = {} as Facets;

	// create facet for concepts
	if (conceptFacets) {
		// Note that creating facets for concepts should ensure align with the list of dataset results
		// Unfortunately, concept facets are always returned from the backend without taking into consideration any applied filters
		// As a result, we need to check and update the aggregates as necessary
		facets[CONCEPT_FACETS_FIELD] = [];
		const conceptKeys = Object.keys(conceptFacets.facets.concepts);
		conceptKeys.forEach((conceptKey) => {
			const concept = conceptFacets?.facets.concepts[conceptKey];
			facets[CONCEPT_FACETS_FIELD].push({ key: concept.name ?? conceptKey, value: concept.count });
		});
	}

	// create facets from specific dataset fields
	DATASET_FACET_FIELDS.forEach((field) => {
		// exclude dataset ID as a facet since it is created from mapping concepts
		if (field !== ID) {
			const facetForField = aggField(field);
			if (facetForField.length > 0) {
				facets[field] = facetForField;
			}
		}
	});

	return facets;
};

// Merging facets who share the same key requires custom logic, e.g.,
//  XDD documents of "type" [fulltext] and Models of "type": [model, dataset]
//  should be merged into one facet representing the overall "type" of result
// @ts-ignore
// eslint-disable-next-line consistent-return
function mergeCustomizer(objValue: any, srcValue: any) {
	if (isArray(objValue)) {
		return objValue.concat(srcValue);
	}
	// return null;
}

export const getFacets = (results: SearchResults[], resultType: ResourceType | string) => {
	let facets = {} as Facets;
	if (results.length > 0) {
		results.forEach((resultsObj) => {
			if (resultsObj.searchSubsystem === resultType || resultType === ResourceType.ALL) {
				// extract facets based on the result type
				// because we would have different facets for different result types
				// e.g., XDD will have facets that leverage the XDD fields and stats
				if (
					resultsObj.searchSubsystem === ResourceType.XDD ||
					resultsObj.searchSubsystem === ResourceType.MODEL ||
					resultsObj.searchSubsystem === ResourceType.DATASET
				) {
					facets = mergeWith(facets, resultsObj.facets, mergeCustomizer);
				}
			}
		});
	}
	return facets;
};

export const getFacetsDisplayNames = (resultType: string, key: string) => {
	let hits = 0;

	switch (resultType) {
		case ResourceType.XDD:
			return XDD_DISPLAY_NAMES[key];
		case ResourceType.MODEL:
			return MODEL_DISPLAY_NAMES[key];
		case ResourceType.DATASET:
			return DATASET_DISPLAY_NAMES[key];
		case ResourceType.ALL:
			// merge display names from all results types,
			//  exclude fields that exist in more than once (e.g., 'type' for models and XDD documents),
			//  and attempt to return the display-name based on the input key
			[MODEL_DISPLAY_NAMES, XDD_DISPLAY_NAMES].forEach((d) => {
				if (d[key] !== undefined) hits += 1;
			});
			if (hits === 1) {
				const displayName = {
					...MODEL_DISPLAY_NAMES,
					...MODEL_DISPLAY_NAMES,
					...XDD_DISPLAY_NAMES
				}[key];
				console.log(displayName);
				return displayName;
			}
			return key;
		default:
			return key;
	}
};
