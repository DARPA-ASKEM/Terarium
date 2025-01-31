<template>
	<tera-modal @modal-mask-clicked="emit('close-modal')">
		<template #header>
			<div class="flex align-items-center justify-space-between">
				<h4 class="w-full mr-4">{{ id }} matrix</h4>
				<div class="flex align-items-center gap-2 white-space-nowrap">
					<InputSwitch
						inputId="matrixShouldEval"
						v-model="matrixShouldEval"
						:binary="true"
						label="Evaluate expressions?"
					/>
					<label for="matrixShouldEval">Evaluate expressions</label>
				</div>
			</div>
		</template>
		<template #default>
			<tera-stratified-matrix
				v-bind="props"
				:should-eval="matrixShouldEval"
				@update-cell-values="(configsToUpdate: any) => emit('update-cell-values', configsToUpdate)"
			/>
		</template>
		<template #footer>
			<Button label="OK" @click="emit('close-modal')" />
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import InputSwitch from 'primevue/inputswitch';
import Button from 'primevue/button';
import TeraModal from '@/components/widgets/tera-modal.vue';
import { StratifiedMatrix } from '@/types/Model';
import type { MiraModel, MiraTemplateParams } from '@/model-representation/mira/mira-common';
import TeraStratifiedMatrix from './tera-stratified-matrix.vue';

const props = defineProps<{
	mmt: MiraModel;
	mmtParams: MiraTemplateParams;
	id: string;
	stratifiedMatrixType: StratifiedMatrix;
	isReadOnly?: boolean;
}>();

const emit = defineEmits(['close-modal', 'update-cell-values']);

const matrixShouldEval = ref(false);
</script>
