<template>
	<section>
		<div v-if="matrix.length > MATRIX_THRESHOLD || matrix[0].length > MATRIX_THRESHOLD">
			Matrix is {{ matrix.length }}x{{ matrix[0].length }}
		</div>
		<section v-else class="matrix">
			<h5>
				<span>{{ subject }}</span>
				<i class="pi pi-arrow-right" />
				<span>{{ outcome }}</span>
			</h5>
			<div class="col-labels">
				<label v-for="(col, colIdx) in matrix[0]" :key="colIdx">
					{{ col.colCriteria }}
				</label>
			</div>
			<div class="row-labels">
				<label v-for="(row, rowIdx) in matrix" :key="rowIdx">
					{{ row[0].rowCriteria }}
				</label>
			</div>
			<div class="matrix-grid">
				<template v-for="row in matrix">
					<div class="cell" :class="{ 'filled-cell': cell.content.id }" v-for="(cell, colIdx) in row" :key="colIdx" />
				</template>
			</div>
		</section>
		<p class="ml-auto">Click to open</p>
	</section>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { StratifiedMatrix } from '@/types/Model';
import type { MiraModel, MiraTemplateParams } from '@/model-representation/mira/mira-common';
import { extractTemplateMatrix } from '@/model-representation/mira/mira-util';
import { collapseTemplates } from '@/model-representation/mira/mira';
import { logger } from '@/utils/logger';

const props = defineProps<{
	mmt: MiraModel;
	mmtParams: MiraTemplateParams;
	id: string;
	stratifiedMatrixType: StratifiedMatrix;
}>();

const matrix = ref<any>([]);
const subject = ref('');
const outcome = ref('');

const matrixColLen = computed(() => matrix.value[0]?.length ?? 0);
const MATRIX_THRESHOLD = 20;

// Currently only supports transition/rate matrices
watch(
	() => props.id,
	() => {
		const collapsedTemplates = collapseTemplates(props.mmt);

		// Get the transition matrix for the current template
		const transitionMatrix = collapsedTemplates.matrixMap.get(props.id);
		if (!transitionMatrix) {
			logger.error('Failed to generate transition matrix');
			return;
		}
		matrix.value = extractTemplateMatrix(transitionMatrix).matrix;

		// Get subject and outcome for the current template
		const templateSummary = collapsedTemplates.templatesSummary.find((template) => template.name === props.id);
		if (templateSummary) {
			subject.value = templateSummary.subject;
			outcome.value = templateSummary.outcome;
		}
	},
	{ immediate: true }
);
</script>

<style scoped>
section {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: var(--gap-2);

	& > .matrix {
		display: grid;
		grid-template-columns: auto auto;
		gap: var(--gap-2);
	}

	& > .matrix-grid {
		display: grid;
		grid-template-columns: repeat(v-bind(matrixColLen), 2rem);
		border: 1px solid black;

		& > .cell {
			border: 1px solid black;
			text-align: center;
			height: 2rem;
		}

		& > .filled-cell {
			background-color: rgb(134, 146, 164);
			color: var(--surface-section);
		}
	}
}

.row-labels,
.col-labels {
	display: flex;
	font-size: var(--font-caption);
	color: var(--text-color-subdued);
	& > label {
		display: flex;
		align-items: center;
	}
}

.row-labels {
	flex-direction: column;
	& > label {
		justify-content: end;
		height: 2rem;
	}
}

.col-labels > label {
	writing-mode: vertical-lr;
	transform: scale(-1);
	width: 2rem;
}

h5 {
	display: flex;
	align-items: center;
	gap: var(--gap-2);
}

p {
	font-size: var(--font-caption);
	color: var(--primary-color);
	padding: var(--gap-2) 0;
}
</style>
