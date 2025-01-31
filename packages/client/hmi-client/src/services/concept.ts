/**
 * Concept
 */

import API from '@/api/api';
import type { Curies, DatasetColumn, DKG, EntitySimilarityResult, State, Model } from '@/types/Types';
import { logger } from '@/utils/logger';
import { isEmpty } from 'lodash';
import { CalibrateMap } from '@/services/calibrate-workflow';
import { FIFOCache } from '@/utils/FifoCache';
import { Workflow, WorkflowNode } from '@/types/workflow';

interface Entity {
	id: string;
	groundings?: any[];
}

interface EntityMap {
	source: string;
	target: string;
}

const curieNameCache = new Map<string, string>();

const currieEntityCache = new FIFOCache<Promise<{ data: any; status: number }>>(400);
/**
 * Get DKG entities, either single ones or multiple at a time
 */
async function getCuriesEntities(curies: Array<string>): Promise<Array<DKG> | null> {
	try {
		const cacheKey = curies.toString();

		let promise = currieEntityCache.get(cacheKey);
		if (!promise) {
			promise = API.get(`/dkg/currie/${curies.toString()}`).then((res) => ({ data: res.data, status: res.status }));
			currieEntityCache.set(cacheKey, promise);
		}

		// If a rename function is defined, loop over the first row
		const response = await promise;
		if (response?.status === 200 && response?.data) return response.data;
		if (response?.status === 204) console.warn('No DKG entities found for curies:', curies);
		return null;
	} catch (err) {
		logger.error(err);
		return null;
	}
}

async function searchCuriesEntities(query: string): Promise<Array<DKG>> {
	try {
		const response = await API.get('/dkg/search', {
			params: {
				term: query
			}
		});
		if (response?.status !== 200) return [];
		return response?.data ?? [];
	} catch (error) {
		logger.error(`There was an issue searching for mira query: ${query}`, { showToast: false });
		return [];
	}
}

/**
 * Hit MIRA to get pairwise similarities between elements referenced by curies in the first list and second list.
 * @input a List of curies (strings) for each source, and target.
 * @return EntitySimilarityResult[] - The source and target curies and their corresponding cosine_distance
  Sample:
	"sources": ["ido:0000514"],
  "targets": ["doid:0081014", "cido:0000180"]

  Output:
	[
		{
			"source": "ido:0000514",
			"target": "doid:0081014",
			"distance": 1.3786096032625137
		},
		{
			"source": "ido:0000514",
			"target": "cido:0000180",
			"distance": 0.8597939628230753
		}
	]
 */
async function getEntitySimilarity(
	sources: string[],
	targets: string[]
): Promise<Array<EntitySimilarityResult> | null> {
	try {
		const response = await API.post('/mira/entity-similarity', { sources, targets } as Curies);
		if (response?.status !== 200) return null;
		return response?.data ?? null;
	} catch (error) {
		logger.error(error, { showToast: false });
		return null;
	}
}

const getNameOfCurieCached = async (curie: string): Promise<string> => {
	if (isEmpty(curie)) return '';
	if (!curieNameCache.has(curie)) {
		const response = await getCuriesEntities([curie]);
		curieNameCache.set(curie, response?.[0]?.name ?? '');
	}
	return curieNameCache.get(curie) ?? '';
};

function getCurieFromGroundingIdentifier(identifier: Object | undefined): string {
	if (!!identifier && !isEmpty(identifier)) {
		const [key, value] = Object.entries(identifier)[0];
		return `${key}:${value}`;
	}
	return '';
}

function parseCurie(curie: string | undefined): { [key: string]: string } {
	if (!curie) return {};
	const key = curie.split(':')[0];
	const value = curie.split(':')[1];
	return { [key]: value };
}

// Takes in 2 lists of generic {id, groundings} and returns the singular
// closest match for each element in list one
const autoEntityMapping = async (sourceEntities: Entity[], targetEntities: Entity[], acceptableDist?: number) => {
	const result = [] as EntityMap[];
	const acceptableDistance = acceptableDist ?? 0.5;

	// join all source and target for 1 mira call
	const allSourceGroundings = sourceEntities.flatMap(({ groundings }) => groundings ?? []);
	const allTargetGroundings = targetEntities.flatMap(({ groundings }) => groundings ?? []);

	// Take out any duplicates
	const distinctSourceGroundings = [...new Set(allSourceGroundings)];
	const distinctTargetGroundings = [...new Set(allTargetGroundings)];

	const allSimilarity = await getEntitySimilarity(distinctSourceGroundings, distinctTargetGroundings);
	if (!allSimilarity) return result;
	// Filter out anything with a similarity too small
	const filteredSimilarity = allSimilarity.filter((ele) => ele.similarity >= acceptableDistance);
	const validSources: any[] = [];
	const validTargets: any[] = [];

	// Join results back with source and target
	filteredSimilarity.forEach((similarity) => {
		// Find all Sources associated with this similarity
		sourceEntities.forEach((source) => {
			if (source.groundings?.includes(similarity.source)) {
				validSources.push({
					sourceId: source.id,
					sourceKey: similarity.source,
					targetKey: similarity.target,
					distance: similarity.similarity
				});
			}
		});
		// Find all targets associated with this similarity
		targetEntities.forEach((target) => {
			if (target.groundings?.includes(similarity.target)) {
				validTargets.push({
					targetId: target.id,
					sourceKey: similarity.source,
					targetKey: similarity.target,
					distance: similarity.similarity
				});
			}
		});
	});

	// for each distinct source, find its highest matching target:
	const distinctSources = [...new Set(validSources.map((ele) => ele.sourceId))];
	distinctSources.forEach((distinctSourceId) => {
		let currentDistance = -Infinity;
		let currentTargetId = '';
		validSources.forEach((source) => {
			if (distinctSourceId === source.sourceId) {
				validTargets.forEach((target) => {
					// Note here we are using the source and target groundings as a key
					if (
						source.sourceKey === target.sourceKey &&
						source.targetKey === target.targetKey &&
						target.distance > currentDistance
					) {
						currentDistance = target.distance;
						currentTargetId = target.targetId;
					}
				});
			}
		});

		// Match by string if no target is found
		if (isEmpty(currentTargetId)) {
			targetEntities.some((target) => {
				if (target.id.startsWith(distinctSourceId) || distinctSourceId.startsWith(target.id)) {
					currentTargetId = target.id;
					return true; // stops the loop
				}
				return false; // continues the loop
			});
		}

		result.push({ source: distinctSourceId, target: currentTargetId });
	});

	// Match id strings for the remainder if source entities that aren't mapped
	sourceEntities.forEach((source) => {
		targetEntities.forEach((target) => {
			if (
				!distinctSources.includes(source.id) &&
				(target.id.startsWith(source.id.toLocaleLowerCase()) || source.id.startsWith(target.id.toLocaleLowerCase()))
			) {
				result.push({ source: source.id, target: target.id });
			}
		});
	});

	return result;
};

// Takes in a list of states and a list of dataset columns.
// Transforms them into generic entities with {id, groundings}
// Uses autoEntityMapping to determine 1:1 mapping
// rewrites result in form {modelVariable, datasetVariable}
const autoCalibrationMapping = async (modelOptions: State[], datasetOptions: DatasetColumn[]) => {
	const result = [] as CalibrateMap[];
	const acceptableDistance = 0.5;
	const sourceEntities: Entity[] = [];
	const targetEntities: Entity[] = [];

	// Fill sourceEntities with modelOptions
	modelOptions.forEach((state) => {
		const groundings = state.grounding?.identifiers
			? Object.entries(state.grounding.identifiers).map((ele) => ele.join(':'))
			: undefined;
		sourceEntities.push({ id: state.id, groundings });
	});

	// Fill targetEntities with datasetOptions
	datasetOptions.forEach((col) => {
		targetEntities.push({ id: col.name ?? '', groundings: [col.grounding] });
	});

	const entityResult = await autoEntityMapping(sourceEntities, targetEntities, acceptableDistance);

	// rename result to CalibrateMap for users of this function
	entityResult.forEach((entity) => {
		result.push({ modelVariable: entity.source, datasetVariable: entity.target });
	});
	if (isEmpty(result)) {
		result.push({ modelVariable: '', datasetVariable: '' });
	}
	return result;
};

// Takes in two lists of states
// Transforms them into generic entities with {id, groundings}
// Uses autoEntityMapping to determine 1:1 mapping
// rewrites result in form {modelOneVariable, modelTwoVariable}
const autoModelMapping = async (modelOneOptions: State[], modelTwoOptions: State[]) => {
	const result = [] as any[];
	const acceptableDistance = 0.5;
	const sourceEntities: Entity[] = [];
	const targetEntities: Entity[] = [];

	// Fill sourceEntities with modelOneOptions
	modelOneOptions.forEach((state) => {
		const groundings = state.grounding?.identifiers
			? Object.entries(state.grounding.identifiers).map((ele) => ele.join(':'))
			: undefined;
		sourceEntities.push({ id: state.id, groundings });
	});

	// Fill targetEntities with datasetOptions
	modelTwoOptions.forEach((state) => {
		const groundings = state.grounding?.identifiers
			? Object.entries(state.grounding.identifiers).map((ele) => ele.join(':'))
			: undefined;
		targetEntities.push({ id: state.id, groundings });
	});

	const entityResult = await autoEntityMapping(sourceEntities, targetEntities, acceptableDistance);

	// rename result to CalibrateMap for users of this function
	entityResult.forEach((entity) => {
		result.push({ modelOneVariable: entity.source, modelTwoVariable: entity.target });
	});
	return result;
};

/**
 * Compare multiple models and return the concepts that are common between them
 * @param modelIds - List of model ids to compare
 * @returns
 */
interface CompareModelsConceptsRequest {
	modelIds: Array<Model['id']>;
	workflowId?: Workflow['id'];
	nodeId?: WorkflowNode<any>['id'];
}
interface CompareModelsConceptsResponse {
	concept_context_comparison?: string;
	tabular_comparison?: { [key: string]: string };
	concept_graph_comparison?: { [key: string]: string };
}
async function getCompareModelConcepts(
	modelIds: Array<Model['id']>,
	workflowId?: Workflow['id'],
	nodeId?: WorkflowNode<any>['id']
) {
	const request: CompareModelsConceptsRequest = { modelIds, workflowId, nodeId };
	const response = await API.post('/mira/compare-models-concepts', request);
	if (response?.status !== 200) return null;
	return (response?.data.response as CompareModelsConceptsResponse) ?? null;
}

export {
	getCuriesEntities,
	getEntitySimilarity,
	searchCuriesEntities,
	getNameOfCurieCached,
	getCurieFromGroundingIdentifier,
	parseCurie,
	autoModelMapping,
	autoCalibrationMapping,
	autoEntityMapping,
	getCompareModelConcepts,
	type CompareModelsConceptsResponse
};
