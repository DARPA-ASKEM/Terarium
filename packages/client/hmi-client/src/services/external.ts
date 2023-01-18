/**
 * External (assets and intermediates)
 */

import API from '@/api/api';
import { PublicationAsset } from '@/types/XDD';

/**
 * Get external publication asset linked by a given project asset/doc id
 * @docId string - represents a specific project asset/doc id
 * @return PublicationAsset|null - the specific publication info including its xdd url, or null if none returned by API
 */
async function getPublication(docId: string): Promise<PublicationAsset | null> {
	const response = await API.get(`/external/publications/${docId}`);
	return response?.data ?? null;
}

/**
 * Get external publication asset in bulk given their internal TDS IDs
 * @docId string array - represents a list of specific project asset/doc id
 * @return PublicationAsset[]|null - the specific publication info including its xdd url, or null if none returned by API
 */
async function getBulkPublicationAssets(docIDs: string[]) {
	const result: PublicationAsset[] = [];
	const promiseList = [] as Promise<PublicationAsset | null>[];
	docIDs.forEach((docId) => {
		promiseList.push(getPublication(docId));
	});
	const responsesRaw = await Promise.all(promiseList);
	responsesRaw.forEach((r) => {
		if (r) {
			result.push(r);
		}
	});
	return result;
}

/**
 * add external publication asset
 * @body PublicationAsset - represents the metadata (xdd) url of the asset to be added
 * @return {id: string}|null - the id of the inserted asset, or null if none returned by API
 */
async function addPublication(body: PublicationAsset): Promise<{ id: string } | null> {
	// FIXME: handle cases where assets is already added to the project
	const response = await API.post('/external/publications', body);
	return response?.data ?? null;
}

export { getPublication, getBulkPublicationAssets, addPublication };
