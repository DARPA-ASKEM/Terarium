<template>
	<tera-asset :id="assetId" :name="document?.name ?? ''" :is-loading="documentLoading" :is-document="true">
		<p class="pl-3" v-if="documentLoading">Loading...</p>
		<tera-pdf-embed v-if="pdfLink" :pdf-link="pdfLink" :title="document?.name || ''" />
		<tera-text-editor v-else-if="docText" :initial-text="docText" />
	</tera-asset>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import TeraAsset from '@/components/asset/tera-asset.vue';
import TeraTextEditor from '@/components/documents/tera-text-editor.vue';
import TeraPdfEmbed from '@/components/widgets/tera-pdf-embed.vue';
import { downloadDocumentAsset, getDocumentAsset, getDocumentFileAsText } from '@/services/document-assets';
import { type DocumentAsset } from '@/types/Types';

const props = defineProps<{
	assetId: string;
}>();

const document = ref<DocumentAsset | null>(null);
const documentLoading = ref(false);
const docText = ref<string | null>(null);
const pdfLink = ref<string | null>(null);

watch(
	() => props.assetId,
	async (assetId, oldAssetId) => {
		if (assetId === oldAssetId) return;
		if (assetId) {
			pdfLink.value = null;
			documentLoading.value = true;
			document.value = await getDocumentAsset(assetId);
			const filename = document.value?.fileNames?.[0];

			const isPdf = filename?.endsWith('.pdf');
			if (document.value?.id && filename) {
				if (isPdf) {
					pdfLink.value = (await downloadDocumentAsset(props.assetId, filename)) ?? null;
				} else {
					docText.value = (await getDocumentFileAsText(document.value.id, filename)) ?? null;
				}
			}
			documentLoading.value = false;
		} else {
			document.value = null;
		}
	},
	{ immediate: true }
);
</script>
