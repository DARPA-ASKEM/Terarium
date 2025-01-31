<template>
	<tera-modal v-if="isVisible">
		<template #header> Create a new intervention policy </template>
		<tera-input-text
			placeholder="Policy name"
			v-model="newPolicyName"
			auto-focus
			@keydown.enter="emit('create', newPolicyName)"
		/>
		<template #footer>
			<Button label="Create" @click="emit('create', newPolicyName)" />
			<Button severity="secondary" label="Close" @click="onClose"></Button>
		</template>
	</tera-modal>
</template>

<script setup lang="ts">
import TeraInputText from '@/components/widgets/tera-input-text.vue';
import TeraModal from '@/components/widgets/tera-modal.vue';
import Button from 'primevue/button';
import { ref } from 'vue';

defineProps<{
	isVisible: boolean;
}>();

const emit = defineEmits(['close', 'create']);

const onClose = () => {
	newPolicyName.value = '';
	emit('close');
};

const newPolicyName = ref('');
</script>
