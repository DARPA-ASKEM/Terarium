import { AssetType, ClientEventType, Grounding, ProgrammingLanguage, ProgressState, StatusUpdate } from '@/types/Types';
import { ProjectPages } from './Project';

export interface FeatureConfig {
	isPreview: boolean;
}

export enum DrilldownTabs {
	Wizard = 'Wizard',
	Notebook = 'Notebook'
}

export enum CiemssPresetTypes {
	Fast = 'Fast',
	Normal = 'Normal'
}

export enum ParamType {
	CONSTANT,
	DISTRIBUTION,
	TIME_SERIES,
	MATRIX,
	EXPRESSION
}

export interface ModelConfigTableData {
	id: string;
	name: string;
	type: ParamType;
	description: string;
	concept: Grounding;
	unit?: string;
	value: any;
	source: string;
	visibility: boolean;
	tableFormattedMatrix?: ModelConfigTableData[];
}

// Side panel
export type SidePanelTab = {
	name: string;
	icon?: string;
	imgSrc?: string;
	isGreyscale?: string;
	badgeCount?: number;
};

export type AssetRoute = {
	assetId: string;
	pageType: AssetType | ProjectPages;
};

export interface AssetItem extends AssetRoute {
	icon?: string;
	assetName?: string;
	assetCreatedOn?: string;
}

// TODO this should come from the back end, and we should also have maps for the "categories" of types (artifacts, models, datasets, etc)
export enum AcceptedTypes {
	PDF = 'application/pdf',
	CSV = 'text/csv',
	TXT = 'text/plain',
	MD = 'text/markdown',
	PY = 'text/x-python-script',
	R = 'text/x-r',
	JL = 'application/julia',
	NC = 'application/x-netcdf',
	JSON = 'application/json',
	XML = 'application/xml',
	SBML = 'application/sbml+xml',
	MDL = `application/vnd.vensim.mdl`,
	XMILE = 'application/vnd.stella.xmile',
	ITMX = 'application/vnd.stella.itmx',
	STMX = 'application/vnd.stella.stmx',
	MODELCONFIG = 'application/zip',
	PROJECTCONFIG = 'application/zip'
}

export enum AcceptedExtensions {
	PDF = 'pdf',
	CSV = 'csv',
	TXT = 'txt',
	MD = 'md',
	PY = 'py',
	R = 'r',
	JL = 'jl',
	// NetCDF format
	NC = 'nc',
	// Model file extensions
	JSON = 'json',
	// SBML formats
	XML = 'xml',
	SBML = 'sbml',
	// Vensim format
	MDL = 'mdl',
	// Stella formats
	XMILE = 'xmile',
	ITMX = 'itmx',
	STMX = 'stmx',
	// proprietary formats
	MODELCONFIG = 'modelconfig',
	PROJECTCONFIG = 'project'
}

export enum AMRSchemaNames {
	PETRINET = 'petrinet',
	REGNET = 'regnet',
	STOCKFLOW = 'stockflow',
	DECAPODES = 'decapodes'
}

export interface PDFExtractionResponseType {
	text: string;
	images: string[];
}

export interface Position {
	x: number;
	y: number;
}
export enum ModelServiceType {
	TA1 = 'SKEMA-MIT',
	TA4 = 'GoLLM'
}

export interface CompareModelsResponseType {
	title: string;
	summary: string;
	structuralComparison: StructuralComparison;
	metaComparison: MetadataComparison;
}

interface StructuralComparison {
	states: Comparison;
	parameters: Comparison;
	transitions: Comparison;
	observables: Comparison;
}

interface MetadataComparison {
	details: Comparison;
	uses: Comparison;
	biasRisksLimitations: Comparison;
	observables: Comparison;
}

interface Comparison {
	common: string;
	unique: string;
	conclusion: string;
}

export type ModelEnrichmentStatusUpdate = StatusUpdate<{ modelId: string; workflowId: string; nodeId: string }>;

export type ExtractionStatusUpdate = StatusUpdate<{ documentId: string }>;
export type CloneProjectStatusUpdate = StatusUpdate<{ projectId: string }>;
export interface NotificationItem extends NotificationItemStatus, AssetRoute {
	notificationGroupId: string;
	type: ClientEventType;
	typeDisplayName: string;
	sourceName: string;
	context: string;
	projectId?: string;
	nodeId?: string;
	lastUpdated: number;
	acknowledged: boolean;
	supportCancel: boolean;
}
export interface NotificationItemStatus {
	status: ProgressState;
	msg: string;
	error: string;
	progress?: number;
}

export enum ChartSettingType {
	VARIABLE = 'variable',
	VARIABLE_OBSERVABLE = 'variable-observable',
	VARIABLE_COMPARISON = 'variable-comparison',
	VARIABLE_ENSEMBLE = 'variable-ensemble',
	DISTRIBUTION_COMPARISON = 'distribution-comparison',
	ERROR_DISTRIBUTION = 'error-distribution',
	INTERVENTION = 'intervention',
	SENSITIVITY = 'sensitivity'
}

export type ChartSetting =
	| ChartSettingBase
	| ChartSettingEnsembleVariable
	| ChartSettingSensitivity
	| ChartSettingComparison;

export interface ChartSettingEnsembleVariable extends ChartSettingBase, ChartSettingEnsembleVariableOptions {
	type: ChartSettingType.VARIABLE_ENSEMBLE;
}

export interface ChartSettingSensitivity extends ChartSettingBase, ChartSettingSensitivityOptions {
	type: ChartSettingType.SENSITIVITY;
}

export interface ChartSettingComparison extends ChartSettingBase {
	type: ChartSettingType.VARIABLE_COMPARISON;
	smallMultiples?: boolean;
	shareYAxis?: boolean;
	/** If true, also plot the pre-calibration or base simulation (without the intervention) data */
	showBeforeAfter?: boolean;
	/** Normalize data by total strata population. Only supported for stratified models */
	normalize?: boolean;
}

export interface ChartSettingEnsembleVariableOptions {
	showIndividualModels: boolean;
	relativeToEnsemble: boolean;
	showIndividualModelsWithWeight?: boolean;
}
export interface ChartSettingSensitivityOptions {
	selectedInputVariables: string[];
	timepoint: number;
}

export interface ChartSettingBase {
	id: string;
	name: string;
	selectedVariables: string[];
	type: ChartSettingType;
	primaryColor?: string;
	scale?: string;
	smallMultiples?: boolean;
	hideInNode?: boolean;
	shareYAxis?: boolean;
	showQuantiles?: boolean;
	quantiles?: number[];
}

export const ProgrammingLanguageVersion: { [key in ProgrammingLanguage]: string } = {
	[ProgrammingLanguage.Python]: 'python3',
	[ProgrammingLanguage.R]: 'ir',
	[ProgrammingLanguage.Julia]: 'julia-1.10',
	[ProgrammingLanguage.Zip]: 'zip'
};

/**
 * Returns an array of options for programming languages.
 * Each option is an object with a `name` property that is a `ProgrammingLanguage` and a `value` property that is the corresponding version string.
 * The `Zip` programming language is excluded from the options.
 * @returns {Array} An array of options for programming languages.
 */
export const programmingLanguageOptions = (): { name: string; value: string; disabled: boolean }[] =>
	Object.values(ProgrammingLanguage)
		.filter((lang) => lang !== ProgrammingLanguage.Zip)
		.map((lang) => ({
			name: lang && `${lang[0].toUpperCase() + lang.slice(1)} (${ProgrammingLanguageVersion[lang]})`,
			value: ProgrammingLanguageVersion[lang],
			disabled: lang === ProgrammingLanguage.Julia
		}));

export enum CalendarDateType {
	DATE = 'date',
	MONTH = 'month',
	YEAR = 'year'
}
