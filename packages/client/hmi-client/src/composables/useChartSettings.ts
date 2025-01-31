import { cloneDeep } from 'lodash';
import { ref, computed, watch } from 'vue';
import {
	ChartSetting,
	ChartSettingComparison,
	ChartSettingEnsembleVariable,
	ChartSettingSensitivity,
	ChartSettingType
} from '@/types/common';
import {
	EnsembleVariableChartSettingOption,
	removeChartSettingById,
	updateChartSettingsBySelectedVariables,
	updateAllChartSettings,
	updateSensitivityChartSettingOption,
	CHART_SETTING_WITH_QUANTILES_OPTIONS,
	createNewChartSetting,
	isChartSettingComparisonVariable,
	getQauntileChartSettingOptions
} from '@/services/chart-settings';
import { WorkflowNode } from '@/types/workflow';

/**
 * Composable to manage chart settings for a given workflow node.
 *
 * @param props - The properties passed to the composable, including the workflow node.
 * @param emit - The emit function to trigger events.
 *
 * @returns An object containing chart settings and methods to manipulate them.
 */
export function useChartSettings(
	props: { node: WorkflowNode<{ chartSettings: ChartSetting[] | null }> },
	emit: (...args: any[]) => void
) {
	const chartSettings = computed(() => props.node.state.chartSettings ?? []);
	const activeChartSettings = ref<ChartSetting | null>(null);
	const comparisonChartsSettingsSelection = computed<{ [settingId: string]: string[] }>(() =>
		selectedComparisonChartSettings.value.reduce((acc, setting) => {
			acc[setting.id] = setting.selectedVariables;
			return acc;
		}, {})
	);

	// Computed properties to filter chart settings by type
	const selectedParameterSettings = computed(() =>
		chartSettings.value.filter((setting) => setting.type === ChartSettingType.DISTRIBUTION_COMPARISON)
	);
	const selectedInterventionSettings = computed(() =>
		chartSettings.value.filter((setting) => setting.type === ChartSettingType.INTERVENTION)
	);
	const selectedVariableSettings = computed(() =>
		chartSettings.value.filter((setting) => setting.type === ChartSettingType.VARIABLE)
	);
	const selectedEnsembleVariableSettings = computed(
		() =>
			chartSettings.value.filter(
				(setting) => setting.type === ChartSettingType.VARIABLE_ENSEMBLE
			) as ChartSettingEnsembleVariable[]
	);
	const selectedErrorVariableSettings = computed(() =>
		chartSettings.value.filter((setting) => setting.type === ChartSettingType.ERROR_DISTRIBUTION)
	);
	const selectedComparisonChartSettings = computed(() => chartSettings.value.filter(isChartSettingComparisonVariable));

	const selectedSensitivityChartSettings = computed(
		() =>
			chartSettings.value.filter(
				(setting) => setting.type === ChartSettingType.SENSITIVITY
			) as ChartSettingSensitivity[]
	);

	watch(chartSettings, (settings) => {
		// Update active chart settings
		if (activeChartSettings.value) {
			const updated = settings.find((setting) => setting.id === activeChartSettings.value?.id);
			activeChartSettings.value = updated ?? null;
		}
	});

	const setActiveChartSettings = (setting: ChartSetting | null) => {
		activeChartSettings.value = setting;
	};

	// Methods to manage chart settings
	const removeChartSettings = (chartId: string) => {
		emit('update-state', {
			...props.node.state,
			chartSettings: removeChartSettingById(chartSettings.value, chartId)
		});
	};

	const updateChartSettings = (selectedVariables: string[], type: ChartSettingType) => {
		emit('update-state', {
			...props.node.state,
			chartSettings: updateChartSettingsBySelectedVariables(chartSettings.value, type, selectedVariables)
		});
	};

	const addEmptyComparisonChart = () => {
		emit('update-state', {
			...props.node.state,
			chartSettings: [
				...chartSettings.value,
				createNewChartSetting('', ChartSettingType.VARIABLE_COMPARISON, [], {
					...getQauntileChartSettingOptions(chartSettings.value)
				})
			]
		});
	};

	const updateComparisonChartSetting = (chartId: string, selectedVariables: string[]) => {
		const state = cloneDeep(props.node.state);
		if (!state.chartSettings) return;
		const setting = state.chartSettings.find(
			(settings) => settings.id === chartId && settings.type === ChartSettingType.VARIABLE_COMPARISON
		) as ChartSettingComparison | undefined;
		if (!setting) return;
		Object.assign(setting, { selectedVariables, name: selectedVariables.join(', ') });
		if (setting.smallMultiples === undefined && selectedVariables.length > 5) {
			// If there are more than 5 variables and the option isn't set yet, enable small multiples by default
			setting.smallMultiples = true;
		}
		emit('update-state', state);
	};

	const updateEnsembleVariableSettingOption = (option: EnsembleVariableChartSettingOption, value: boolean) => {
		emit('update-state', {
			...props.node.state,
			chartSettings: updateAllChartSettings(chartSettings.value, { [option]: value }, [
				ChartSettingType.VARIABLE_ENSEMBLE
			])
		});
	};

	const updateQauntilesOptions = (options: { showQuantiles: boolean; quantiles: number[] }) => {
		emit('update-state', {
			...props.node.state,
			chartSettings: updateAllChartSettings(chartSettings.value, options, CHART_SETTING_WITH_QUANTILES_OPTIONS)
		});
	};

	const updateSensitivityChartSettings = (options: {
		selectedVariables: string[];
		selectedInputVariables: string[];
		timepoint: number;
	}) => {
		emit('update-state', {
			...props.node.state,
			chartSettings: updateSensitivityChartSettingOption(chartSettings.value as ChartSettingSensitivity[], options)
		});
	};

	/**
	 * Find and update a chart setting by its id.
	 * @param id - The id of the chart setting to update.
	 * @param update - The partial update to apply to the chart setting.
	 */
	const findAndUpdateChartSettingsById = (id: string, update: Partial<ChartSetting>) => {
		const state = cloneDeep(props.node.state);
		if (state.chartSettings) {
			const setting = state.chartSettings.find((settings) => settings.id === id);
			if (setting) {
				Object.assign(setting, update);
				emit('update-state', state);
			}
		}
	};

	/**
	 * Update the active chart settings with a partial update.
	 * @param update - The partial update to apply to the active chart settings.
	 */
	const updateActiveChartSettings = (update: Partial<ChartSetting>) => {
		findAndUpdateChartSettingsById(activeChartSettings.value?.id ?? '', update);
	};

	return {
		chartSettings,
		activeChartSettings: computed(() => activeChartSettings.value),
		comparisonChartsSettingsSelection,
		selectedVariableSettings,
		selectedEnsembleVariableSettings,
		selectedErrorVariableSettings,
		selectedParameterSettings,
		selectedInterventionSettings,
		selectedComparisonChartSettings,
		selectedSensitivityChartSettings,
		setActiveChartSettings,
		updateActiveChartSettings,
		removeChartSettings,
		updateChartSettings,
		updateEnsembleVariableSettingOption,
		updateQauntilesOptions,
		updateSensitivityChartSettings,
		findAndUpdateChartSettingsById,
		addEmptyComparisonChart,
		updateComparisonChartSetting
	};
}
