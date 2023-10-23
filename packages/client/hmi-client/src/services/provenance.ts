/**
 * Provenance
 */

import API from '@/api/api';
import { logger } from '@/utils/logger';
import { ResourceType, ResultType } from '@/types/common';
import { ProvenanceQueryParam, ProvenanceType } from '@/types/Types';
import { ProvenanceResult } from '@/types/Provenance';
import { cloneDeep } from 'lodash';
import { getBulkDatasets } from './dataset';
// eslint-disable-next-line import/no-cycle
import { getBulkXDDDocuments } from './data';
import { getBulkExternalPublications } from './external';
import { getBulkModels } from './model';
import { getBulkDocumentAssets } from './document-assets';

//

//
// FIXME: currently related artifacts extracted from the provenance graph will be provided
//        as IDs that needs to be fetched, and since no bulk fetch API exists
//        so we are using the following limit to optimize things a bit
const MAX_RELATED_ARTIFACT_COUNT = 5;

/**
 For a document, find related artifacts
	Find models that reference that document
	Find datasets that reference that document
 */
/**
 For a document, find similar content
	Find related documents (xDD)
 */

// API helper function for fetching provenance data
async function getConnectedNodes(
	id: string,
	rootType: ProvenanceType,
	types: ProvenanceType[]
): Promise<ProvenanceResult | null> {
	const body: ProvenanceQueryParam = {
		rootId: id,
		rootType,
		nodes: true,
		hops: 1,
		limit: 10,
		verbose: true,
		types
	};
	const connectedNodesRaw = await API.post('/provenance/connected-nodes', body).catch((error) =>
		logger.error(`Error: ${error}`)
	);

	const connectedNodes: ProvenanceResult = connectedNodesRaw?.data ?? null;
	return connectedNodes;
}

/**
 * Find related artifacts of a given root type
 * @id: id to be used as the root
 * @return ResultType[]|null - the list of all artifacts, or null if none returned by API
 */
async function getRelatedArtifacts(
	id: string,
	rootType: ProvenanceType,
	types: ProvenanceType[] = Object.values(ProvenanceType)
): Promise<ResultType[]> {
	const response: ResultType[] = [];

	if (!rootType) return response;
	const connectedNodes = await getConnectedNodes(id, rootType, types);
	if (connectedNodes) {
		const modelRevisionIDs: string[] = [];
		const externalPublicationIds: string[] = [];
		const datasetIDs: string[] = [];
		let documentAssetIds: string[] = [];

		// For a model/dataset root type:
		//  	Find other model revisions
		//	 	Find document(s) used to referencing the model
		//	 	Find datasets that represent the simulation runs of the model

		// For a document root type:
		//  	Find models that reference that document
		//		Find datasets that reference that document

		// parse the response (sub)graph and extract relevant artifacts
		connectedNodes.result.nodes.forEach((node) => {
			if (rootType !== ProvenanceType.Publication) {
				if (
					node.type === ProvenanceType.Publication &&
					externalPublicationIds.length < MAX_RELATED_ARTIFACT_COUNT
				) {
					externalPublicationIds.push(node.id.toString());
				}
			}

			if (node.type === ProvenanceType.Dataset && datasetIDs.length < MAX_RELATED_ARTIFACT_COUNT) {
				// FIXME: provenance data return IDs as number(s)
				// but the fetch service expects IDs as string(s)
				datasetIDs.push(node.id.toString());
			}

			// will change to Document type when its available in provenance
			if (
				node.type === ProvenanceType.Artifact &&
				documentAssetIds.length < MAX_RELATED_ARTIFACT_COUNT
			) {
				documentAssetIds.push(node.id.toString());
			}

			// TODO: https://github.com/DARPA-ASKEM/Terarium/issues/880
			// if (
			// 	node.type === ProvenanceType.ModelRevision &&
			// 	modelRevisionIDs.length < MAX_RELATED_ARTIFACT_COUNT
			// ) {
			// 	modelRevisionIDs.push(node.id.toString());
			// }
		});

		//
		// FIXME: the provenance API return artifact IDs, but we need the actual objects
		//        so we need to fetch all artifacts using provided IDs
		//

		const datasets = await getBulkDatasets(datasetIDs);
		response.push(...datasets);

		const models = await getBulkModels(modelRevisionIDs);
		response.push(...models);

		// FIXME temporary until we have document types in provenace
		documentAssetIds = cloneDeep(externalPublicationIds);
		const documentAssets = await getBulkDocumentAssets(documentAssetIds);
		response.push(...documentAssets);

		const externalPublications = await getBulkExternalPublications(externalPublicationIds);
		// FIXME: xdd_uri
		const xDDdocuments = await getBulkXDDDocuments(externalPublications.map((p) => p.xdd_uri));
		response.push(...xDDdocuments);
	}

	// NOTE: performing a provenance search returns
	//        a list of Terarium artifacts, which means different types of artifacts
	//        are returned and the explorer view would have to decide to display them
	return response;
}

export enum RelationshipType {
	BEGINS_AT = 'BEGINS_AT',
	CITES = 'CITES',
	COMBINED_FROM = 'COMBINED_FROM',
	CONTAINS = 'CONTAINS',
	COPIED_FROM = 'COPIED_FROM',
	DECOMPOSED_FROM = 'DECOMPOSED_FROM',
	DERIVED_FROM = 'DERIVED_FROM',
	EDITED_FROM = 'EDITED_FROM',
	EQUIVALENT_OF = 'EQUIVALENT_OF',
	EXTRACTED_FROM = 'EXTRACTED_FROM',
	GENERATED_BY = 'GENERATED_BY',
	GLUED_FROM = 'GLUED_FROM',
	IS_CONCEPT_OF = 'IS_CONCEPT_OF',
	PARAMETER_OF = 'PARAMETER_OF',
	REINTERPRETS = 'REINTERPRETS',
	STRATIFIED_FROM = 'STRATIFIED_FROM',
	USES = 'USES'
}
export interface ProvenanacePayload {
	id?: number;
	timestamp?: string;
	relation_type: RelationshipType;
	left: string;
	left_type: ProvenanceType;
	right: string;
	right_type: ProvenanceType;
	user_id?: number;
	concept?: string;
}
async function createProvenance(payload: ProvenanacePayload) {
	const response = await API.post('/provenance', payload);

	const { status, data } = response;
	if (status !== 201) return null;
	return data?.id ?? null;
}

async function getProvenance(id: string) {
	const response = await API.get(`/provenance/${id}`);
	const { data } = response;
	return data ?? null;
}

export function mapResourceTypeToProvenanceType(resourceType: ResourceType): ProvenanceType | null {
	switch (resourceType) {
		case ResourceType.MODEL:
			return ProvenanceType.Model;
		case ResourceType.DATASET:
			return ProvenanceType.Dataset;
		default:
			return null;
	}
}

//
// FIXME: needs to create a similar function to "getRelatedArtifacts"
//        for finding related datasets
//
export { getRelatedArtifacts, createProvenance, getProvenance };
