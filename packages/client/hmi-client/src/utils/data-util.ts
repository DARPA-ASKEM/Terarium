import { ResourceType, ResultType } from '@/types/common';
import { Filters } from '@/types/Filter';
import { isEmpty } from 'lodash';
import { Model, FACET_FIELDS as MODEL_FACET_FIELDS } from '@/types/Model';
import { XDDArticle } from '@/types/XDD';
import IconDocument20 from '@carbon/icons-vue/es/document/20';
import IconDocumentBlank20 from '@carbon/icons-vue/es/document--blank/20';
import IconMachineLearningModel20 from '@carbon/icons-vue/es/machine-learning-model/20';
import IconTableSplit20 from '@carbon/icons-vue/es/table--split/20';
import { Dataset, FACET_FIELDS as DATASET_FACET_FIELDS } from '@/types/Dataset';

// source: https://www.crossref.org/blog/dois-and-matching-regular-expressions/
const DOI_VALIDATION_PATTERN = /^10.\d{4,9}\/[-._;()/:A-Z0-9]+$/i;

export const applyFacetFilters = <T>(
	results: T[],
	filters: Filters,
	resourceType: ResourceType
) => {
	if (isEmpty(filters) || isEmpty(results)) {
		return;
	}

	const { clauses } = filters;
	const ASSET_FACET_FIELDS: string[] =
		resourceType === ResourceType.MODEL ? MODEL_FACET_FIELDS : DATASET_FACET_FIELDS;

	clauses.forEach((clause) => {
		const filterField: string = clause.field; // the field to filter on
		// "filters" may include fields that belong to different types of artifacts
		//  thus make sure to only filter models using Model fields
		if (ASSET_FACET_FIELDS.includes(filterField)) {
			const filterValues = clause.values.map((v) => v.toString()); // array of values to filter upon
			const isNot = !clause.isNot; // is the filter reversed?

			results.splice(
				0,
				results.length,
				...results.filter((asset) => {
					const assetAttribute: any = asset[filterField as keyof T];
					return filterValues.includes(assetAttribute.toString()) === isNot;
				})
			);
		}
	});
};

export const getResourceTypeIcon = (type: string) => {
	switch (type) {
		case ResourceType.MODEL:
			return IconMachineLearningModel20;
		case ResourceType.DATASET:
			return IconTableSplit20;
		case ResourceType.XDD:
			return IconDocumentBlank20;
		default:
			return IconDocument20;
	}
};

// TEMP FUNCTIONS
export function isModel(item: ResultType): item is Model {
	return (<Model>item).content !== undefined;
}

export function isDataset(item: ResultType): item is Dataset {
	return (<Dataset>item).annotations !== undefined;
}

export function isXDDArticle(item: ResultType): item is XDDArticle {
	return (<XDDArticle>item).publisher !== undefined;
}

export function getResourceID(item: ResultType) {
	if (isXDDArticle(item)) {
		return (item as XDDArticle).gddid;
	}
	return item.id;
}
//

/**
 * Validate that the input string is valid.
 *
 * Uses DOI pattern described here: https://www.crossref.org/blog/dois-and-matching-regular-expressions/
 *
 * @param possibleDOI
 * @returns true if DOI is valid
 */
export function validate(possibleDOI?: string): boolean {
	if (!possibleDOI) return false;
	return possibleDOI.match(DOI_VALIDATION_PATTERN) !== null;
}

export function getDocumentDoi(doc: XDDArticle | null) {
	let docIdentifier = '';
	if (doc && doc.identifier.length > 0) {
		const defaultDOI = doc.identifier.find((i) => i.type === 'doi');
		if (defaultDOI) {
			docIdentifier = defaultDOI.id;
		}
	}
	return docIdentifier;
}
