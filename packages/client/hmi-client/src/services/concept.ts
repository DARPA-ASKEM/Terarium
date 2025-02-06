/**
 * Concept
 */

import API from '@/api/api';
import type {
	Curies,
	DatasetColumn,
	DKG,
	EntitySimilarityResult,
	State,
	Model,
	Grounding,
	Observable
} from '@/types/Types';
import { logger } from '@/utils/logger';
import { isEmpty } from 'lodash';
import { CalibrateMap } from '@/services/calibrate-workflow';
import { FIFOCache } from '@/utils/FifoCache';
import { Workflow, WorkflowNode } from '@/types/workflow';

interface Entity {
	id: string;
	grounding: Grounding;
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

async function getDKGFromGroundingIdentifier(identifier: Object): Promise<DKG> {
	const dkg: DKG = { name: '', curie: '', description: '' };
	dkg.curie = getCurieFromGroundingIdentifier(identifier);
	dkg.name = await getNameOfCurieCached(dkg.curie);
	if (isEmpty(dkg.name)) dkg.name = dkg.curie; // in case no name could be found, display the curie
	return dkg;
}

async function getDKGFromGroundingContext(context: Grounding['context']): Promise<DKG[]> {
	let dkgList: DKG[] = [];
	if (!isEmpty(context)) {
		Object.entries(context).forEach(([key, value]) => {
			const dkg: DKG = { name: '', curie: '', description: '' };

			// Test if the value is a curie or a string
			if (value.includes(':')) {
				dkg.curie = value;
				dkg.name = key;
			} else {
				dkg.name = `${key}: ${value}`;
			}
			dkgList.push(dkg);
		});

		// Resolve the name of curies properly
		dkgList = await Promise.all(
			dkgList.map(async (dkg) => {
				if (isEmpty(dkg.curie)) return dkg;
				const newName = await getNameOfCurieCached(dkg.curie);
				if (!isEmpty(newName)) dkg.name = newName;
				return dkg;
			})
		);
	}
	return dkgList;
}

function parseCurieToIdentifier(curie: string | undefined): { [key: string]: string } {
	if (!curie) return {};
	const [key, value] = curie.split(':');
	return { [key]: value };
}

function parseListDKGToGroundingContext(dkgList: DKG[]): { [index: string]: string } {
	const context: Grounding['context'] = {};
	dkgList.forEach((dkg) => {
		if (dkg.name.includes(':')) {
			const [key, value] = dkg.name.split(':');
			context[key] = value;
		} else {
			context[dkg.name] = dkg.curie;
		}
	});
	return context;
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

function getUniqueIdentifierCuriesFromGroundings(groundings: Grounding[]): DKG['curie'][] {
	return [...new Set(groundings.map((grounding) => getCurieFromGroundingIdentifier(grounding.identifiers)))];
}

/**
 * Auto map identifier concept from source to target based on similarity
 * Takes in 2 lists of generic {id, groundings} and returns the singular closest match for each element in list one.
 * Only matches to the lowest list from either the sources or targets.
 */
async function autoMappingGrounding(sources: Entity[], targets: Entity[]): Promise<CalibrateMap[]> {
	// Get all uniques identifier curies to a list to make one call to MIRA
	const sourceCuries = getUniqueIdentifierCuriesFromGroundings(sources.map((source) => source.grounding));
	const targetCuries = getUniqueIdentifierCuriesFromGroundings(targets.map((target) => target.grounding));

	let allSimilarity = await getEntitySimilarity(sourceCuries, targetCuries);
	if (!allSimilarity) return [] as CalibrateMap[];

	// Filter out anything with a similarity too small
	allSimilarity = allSimilarity.filter((ele) => ele.similarity >= 0.5);

	// Check if the source or target list is smaller
	const isSourceSmaller = sources.length < targets.length;

	// Make a list of the longest list to map the curies to the ids
	const curieMap = new Map(
		(isSourceSmaller ? targets : sources).map((entity) => [
			getCurieFromGroundingIdentifier(entity.grounding.identifiers),
			entity.id
		])
	);

	// Go through the smallest list and find similarities
	return (isSourceSmaller ? sources : targets)
		.map((entity) => {
			const curie = getCurieFromGroundingIdentifier(entity.grounding.identifiers);
			const bestMatch = allSimilarity
				.filter((similarity) => (isSourceSmaller ? similarity.source : similarity.target) === curie)
				.reduce((best, curr) => (curr.similarity > best.similarity ? curr : best), {
					similarity: -Infinity,
					target: '',
					source: ''
				});

			const matchedId = curieMap.get(isSourceSmaller ? bestMatch.target : bestMatch.source);

			return matchedId
				? {
						modelVariable: isSourceSmaller ? entity.id : matchedId,
						datasetVariable: isSourceSmaller ? matchedId : entity.id
					}
				: null;
		})
		.filter(Boolean) as CalibrateMap[];
}

/**
 Takes in a list of states/observables and a list of dataset columns.
 Transforms them into generic entities with {id, Grounding}
 rewrites result in form {modelVariable, datasetVariable}
 */
async function autoCalibrationMapping(
	modelOptions: (State | Observable)[],
	datasetColumns: DatasetColumn[]
): Promise<CalibrateMap[]> {
	// Sources are modelOptions
	const sources: Entity[] = modelOptions
		.filter((variable) => variable.grounding)
		.map((variable) => ({ id: variable.id, grounding: variable.grounding }) as Entity);

	// Targets are datasetColumns
	const targets: Entity[] = datasetColumns
		.filter((column) => column.grounding && !isEmpty(column.name))
		.map((column) => ({ id: column.name ?? '', grounding: column.grounding }) as Entity);

	return autoMappingGrounding(sources, targets);
}

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
	getDKGFromGroundingIdentifier,
	getDKGFromGroundingContext,
	parseCurieToIdentifier,
	parseListDKGToGroundingContext,
	autoCalibrationMapping,
	autoMappingGrounding,
	getCompareModelConcepts,
	type CompareModelsConceptsResponse
};
