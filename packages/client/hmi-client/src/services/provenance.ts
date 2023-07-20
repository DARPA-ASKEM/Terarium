/**
 * Provenance
 */

import API from '@/api/api';
import { logger } from '@/utils/logger';
import { ResultType } from '@/types/common';
import { DocumentAsset, ProvenanceQueryParam, ProvenanceType } from '@/types/Types';
import { ProvenanceResult } from '@/types/Provenance';
// eslint-disable-next-line import/no-cycle
import { getBulkDocuments } from './data';
import { getBulkDatasets } from './dataset';
import { getBulkDocumentAssets, getDocument } from './external';
import { getBulkModels } from './model';

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
	rootType: ProvenanceType
): Promise<ProvenanceResult | null> {
	const publication: DocumentAsset | null = await getDocument(id);
	if (!publication) return null;

	const body: ProvenanceQueryParam = {
		rootId: publication.id,
		rootType
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
async function getRelatedArtifacts(id: string, rootType: ProvenanceType): Promise<ResultType[]> {
	const response: ResultType[] = [];

	const connectedNodes = await getConnectedNodes(id, rootType);
	if (connectedNodes) {
		const modelRevisionIDs: string[] = [];
		const documentIDs: string[] = [];
		const datasetIDs: string[] = [];

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
					documentIDs.length < MAX_RELATED_ARTIFACT_COUNT
				) {
					documentIDs.push(node.id.toString());
				}
			}

			if (node.type === ProvenanceType.Dataset && datasetIDs.length < MAX_RELATED_ARTIFACT_COUNT) {
				// FIXME: provenance data return IDs as number(s)
				// but the fetch service expects IDs as string(s)
				datasetIDs.push(node.id.toString());
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

		const documentAssets = await getBulkDocumentAssets(documentIDs);
		// FIXME: xdd_uri
		const documents = await getBulkDocuments(documentAssets.map((p) => p.xdd_uri));
		response.push(...documents);
	}

	// NOTE: performing a provenance search returns
	//        a list of Terarium artifacts, which means different types of artifacts
	//        are returned and the explorer view would have to decide to display them
	return response;
}

//
// FIXME: needs to create a similar function to "getRelatedArtifacts"
//        for finding related datasets
//
export { getRelatedArtifacts };
