/* tslint:disable */
/* eslint-disable */

export interface ClientConfig {
	baseUrl: string;
	clientLogShippingEnabled: boolean;
	clientLogShippingIntervalMillis: number;
	sseHeartbeatIntervalMillis: number;
}

export interface ClientEvent<T> {
	id: string;
	createdAtMs: number;
	type: ClientEventType;
	data: T;
}

export interface ClientLog {
	level: string;
	timestampMillis: number;
	message: string;
	args?: string[];
}

export interface TerariumAsset {
	id?: string;
	name?: string;
	description?: string;
	createdOn?: Date;
	updatedOn?: Date;
	deletedOn?: Date;
	temporary?: boolean;
	publicAsset?: boolean;
}

export interface User {
	id: string;
	createdAtMs: number;
	lastLoginAtMs: number;
	roles: Role[];
	username: string;
	email: string;
	givenName: string;
	familyName: string;
	name: string;
	enabled: boolean;
}

export interface GithubFile {
	type: FileType;
	encoding: string;
	size: number;
	name: string;
	path: string;
	content: string;
	sha: string;
	url: string;
	gitUrl: string;
	htmlUrl: string;
	downloadUrl: string;
	links: Links;
	submoduleGitUrl: string;
	target: string;
	fileCategory: FileCategory;
}

export interface GithubRepo {
	files: { [P in FileCategory]?: GithubFile[] };
	totalFiles: number;
}

export interface Artifact extends TerariumAsset {
	userId: string;
	fileNames: string[];
	metadata?: any;
	concepts?: OntologyConcept[];
}

export interface CsvAsset {
	csv: string[][];
	stats?: CsvColumnStats[];
	headers: string[];
	rowCount: number;
	data: { [index: string]: string }[];
}

export interface CsvColumnStats {
	bins: number[];
	minValue: number;
	maxValue: number;
	mean: number;
	median: number;
	sd: number;
}

export interface Grounding {
	identifiers: Identifier[];
	context?: { [index: string]: any };
}

export interface Identifier {
	curie: string;
	name: string;
}

export interface PresignedURL {
	url: string;
	method: string;
}

export interface ResponseDeleted {
	message: string;
}

export interface ResponseStatus {
	status: number;
}

export interface ResponseSuccess {
	success: boolean;
}

export interface Code extends TerariumAsset {
	files?: { [index: string]: CodeFile };
	repoUrl?: string;
	metadata?: { [index: string]: string };
	project?: Project;
}

export interface CodeFile {
	language: ProgrammingLanguage;
	dynamics: Dynamics;
}

export interface Dynamics {
	name: string;
	description: string;
	block: string[];
}

export interface ActiveConcept extends TerariumAsset {
	curie: string;
}

export interface OntologyConcept {
	id?: string;
	createdOn?: Date;
	updatedOn?: Date;
	deletedOn?: Date;
	curie: string;
	type: TaggableType;
	objectId: string;
	status: OntologicalField;
	activeConcept: ActiveConcept;
}

export interface Dataset extends TerariumAsset {
	userId?: string;
	esgfId?: string;
	dataSourceDate?: Date;
	fileNames?: string[];
	datasetUrls?: string[];
	columns?: DatasetColumn[];
	metadata?: any;
	source?: string;
	grounding?: Grounding;
}

export interface DatasetColumn {
	name: string;
	dataType: ColumnType;
	formatStr?: string;
	annotations: string[];
	metadata?: { [index: string]: any };
	grounding?: Grounding;
	description?: string;
}

export interface AddDocumentAssetFromXDDRequest {
	document: Document;
	projectId: string;
	domain: string;
}

export interface AddDocumentAssetFromXDDResponse {
	documentAssetId: string;
	pdfUploadError: boolean;
	extractionJobId: string;
}

export interface DocumentAsset extends TerariumAsset {
	userId?: string;
	fileNames?: string[];
	documentUrl?: string;
	metadata?: { [index: string]: any };
	source?: string;
	text?: string;
	grounding?: Grounding;
	concepts?: OntologyConcept[];
	assets?: DocumentExtraction[];
}

export interface ExternalPublication extends TerariumAsset {
	title: string;
	xdd_uri: string;
}

export interface Model extends TerariumAssetThatSupportsAdditionalProperties {
	header: ModelHeader;
	userId?: string;
	model: { [index: string]: any };
	properties?: any;
	semantics?: ModelSemantics;
	metadata?: ModelMetadata;
}

export interface ModelConfiguration extends TerariumAssetThatSupportsAdditionalProperties {
	configuration: Model;
	interventions?: Intervention[];
	model_id: string;
}

export interface ModelDescription {
	id: string;
	header: ModelHeader;
	timestamp: Date;
	userId?: string;
}

export interface ModelFramework extends TerariumAssetThatSupportsAdditionalProperties {
	name: string;
	version: string;
	semantics: string;
}

export interface State {
	id: string;
	name?: string;
	description?: string;
	grounding?: ModelGrounding;
	units?: ModelUnit;
}

export interface Transition {
	id: string;
	input: string[];
	output: string[];
	grounding?: ModelGrounding;
	properties?: Properties;
}

export interface Configuration {
	parameters: { [index: string]: ConfigurationParameter };
	initialConditions: { [index: string]: ConfigurationCondition };
	boundryConditions: { [index: string]: ConfigurationCondition };
	datasets: { [index: string]: ConfigurationDataset };
}

export interface ConfigurationCondition {
	_type: string;
	type: string;
	value: string;
	domainMesh: string;
}

export interface ConfigurationDataset {
	_type: string;
	type: string;
	name: string;
	description: string;
	file: ConfigurationDatasetFile;
}

export interface ConfigurationDatasetFile {
	_type: string;
	uri: string;
	format: string;
	shape: number[];
}

export interface ConfigurationHeader {
	id: string;
	description: string;
	name: string;
	parentContext: string;
}

export interface ConfigurationParameter {
	_type: string;
	type: string;
	value: any;
}

export interface Context {
	constants: { [index: string]: ContextConstant };
	spatialConstraints: any;
	temporalConstraints: any;
	primalDualRelations: ContextPrimalDualRelation[];
	meshSubmeshRelations: ContextMeshSubmeshRelation[];
	meshes: ContextMesh[];
}

export interface ContextConstant {
	_type: string;
	value: any;
}

export interface ContextFile {
	uri: string;
	format: string;
}

export interface ContextHeader {
	id: string;
	description: string;
	name: string;
	parentModel: string;
}

export interface ContextMesh {
	id: string;
	description: string;
	dimensionality: any;
	vertexCount: number;
	edgeCount: number;
	faceCount: number;
	volumeCount: number;
	regions: any[];
	checksum: string;
	file: ContextFile;
}

export interface ContextMeshSubmeshRelation {
	mesh: string;
	submesh: string;
	relation: any;
}

export interface ContextPrimalDualRelation {
	primal: string;
	dual: string;
	method: any;
}

export interface DecapodesComponent {
	modelInterface: string[];
	model: DecapodesExpression;
	_type: string;
}

export interface DecapodesConfiguration extends TerariumAsset {
	header: ConfigurationHeader;
	configuration: Configuration;
}

export interface DecapodesContext extends TerariumAsset {
	header: ContextHeader;
	context: Context;
}

export interface DecapodesEquation {
	lhs: any;
	rhs: any;
	_type: string;
}

export interface DecapodesExpression {
	context: any[];
	equations: DecapodesEquation[];
	_type: string;
}

export interface DecapodesTerm {
	name?: string;
	var?: DecapodesTerm;
	symbol?: string;
	space?: string;
	fs?: string[];
	arg?: DecapodesTerm;
	f?: string;
	arg1?: DecapodesTerm;
	arg2?: DecapodesTerm;
	args?: DecapodesTerm[];
	_type: string;
}

export interface NotebookSession extends TerariumAsset {
	data: any;
}

export interface PetriNetModel {
	states: PetriNetState[];
	transitions: PetriNetTransition[];
}

export interface Project extends TerariumAsset {
	userId: string;
	userName?: string;
	authors?: string[];
	overviewContent?: any;
	/**
	 * @deprecated
	 */
	projectAssets: ProjectAsset[];
	codeAssets: Code[];
	metadata?: { [index: string]: string };
	publicProject?: boolean;
	userPermission?: string;
}

export interface ProjectAsset extends TerariumAsset {
	assetId: string;
	assetType: AssetType;
	assetName: string;
	externalRef?: string;
	project: Project;
}

export interface Provenance extends TerariumAsset {
	concept: string;
	relationType: ProvenanceRelationType;
	left: string;
	leftType: ProvenanceType;
	right: string;
	rightType: ProvenanceType;
	userId: string;
}

export interface ProvenanceQueryParam {
	rootId: string;
	rootType: ProvenanceType;
	nodes?: boolean;
	edges?: boolean;
	versions?: boolean;
	types?: ProvenanceType[];
	hops?: number;
	limit?: number;
	verbose?: boolean;
}

export interface ProvenanceSearchResult {
	nodes: ProvenanceNode[];
	edges: ProvenanceEdge[];
}

export interface RegNetBaseProperties {
	name: string;
	grounding: ModelGrounding;
	rate_constant: any;
}

export interface RegNetEdge {
	source: string;
	target: string;
	id: string;
	sign: boolean;
	properties?: RegNetBaseProperties;
}

export interface RegNetModel {
	vertices: RegNetVertex[];
	edges: RegNetEdge[];
	parameters?: RegNetParameter[];
}

export interface RegNetParameter {
	id: string;
	description?: string;
	value?: number;
	grounding?: ModelGrounding;
	distribution?: ModelDistribution;
}

export interface RegNetVertex {
	id: string;
	name: string;
	sign: boolean;
	initial?: any;
	grounding?: ModelGrounding;
	rate_constant?: any;
}

export interface Simulation extends TerariumAsset {
	executionPayload: any;
	resultFiles?: string[];
	type: SimulationType;
	status: ProgressState;
	statusMessage?: string;
	startTime?: Date;
	completedTime?: Date;
	engine: SimulationEngine;
	userId?: string;
	projectId?: string;
	updates: SimulationUpdate[];
}

export interface SimulationUpdate {
	id: string;
	createdOn: Date;
	data: any;
	simulation: Simulation;
}

export interface DocumentsResponseOK extends XDDResponseOK {
	data: Document[];
	nextPage: string;
	scrollId: string;
	hits: number;
	facets: { [index: string]: XDDFacetsItemResponse };
}

export interface EvaluationScenarioSummary {
	name: string;
	userId: string;
	task: string;
	description: string;
	notes: string;
	multipleUsers: boolean;
	timestampMillis: number;
}

export interface ExtractionResponse {
	id: string;
	status: string;
	result: ExtractionResponseResult;
}

export interface ExtractionResponseResult {
	created_at: Date;
	enqueued_at: Date;
	started_at: Date;
	job_error: string;
	job_result: any;
}

export interface ExtractionStatusUpdate {
	notificationGroupId: string;
	documentId: string;
	t: number;
	message: string;
	error: string;
}

export interface FunmanPostQueriesRequest {
	model: Model;
	request: FunmanWorkRequest;
}

export interface FunmanConfig {
	tolerance?: number;
	queueTimeout?: number;
	numberOfProcesses?: number;
	waitTimeout?: number;
	waitActionTimeout?: number;
	solver?: string;
	numSteps?: number;
	stepSize?: number;
	numInitialBoxes?: number;
	saveSmtlib?: boolean;
	drealPrecision?: number;
	drealLogLevel?: string;
	constraintNoise?: number;
	initialStateTolerance?: number;
	drealMcts?: boolean;
	substituteSubformulas?: boolean;
	use_compartmental_constraints?: boolean;
	normalize?: boolean;
	normalization_constant?: number;
}

export interface FunmanInterval {
	ub?: number;
	lb?: number;
	closed_upper_bound?: boolean;
}

export interface FunmanParameter {
	name: string;
	interval: FunmanInterval;
	label: string;
}

export interface FunmanWorkRequest {
	query?: any;
	constraints?: any;
	parameters?: FunmanParameter[];
	config?: FunmanConfig;
	structure_parameters?: any;
}

export interface Curies {
	sources: string[];
	targets: string[];
}

export interface DKG {
	curie: string;
	name: string;
	description: string;
	link: string;
}

export interface EntitySimilarityResult {
	source: string;
	target: string;
	similarity: number;
}

export interface NotificationEvent {
	id: string;
	progress: number;
	state: ProgressState;
	createdOn: Date;
	acknowledgedOn: Date;
	data: any;
}

export interface NotificationGroup {
	id: string;
	userId: string;
	type: string;
	createdOn: Date;
	notificationEvents: NotificationEvent[];
}

export interface PermissionGroup {
	id: string;
	name: string;
	relationship?: string;
	permissionRelationships?: PermissionRelationships;
}

export interface PermissionProject {
	id: string;
	relationship: string;
}

export interface PermissionRelationships {
	permissionGroups: PermissionGroup[];
	permissionUsers: PermissionUser[];
	permissionProjects: PermissionProject[];
}

export interface PermissionUser {
	id: string;
	firstName: string;
	lastName: string;
	email: string;
	roles?: PermissionRole[];
	relationship?: string;
}

export interface S3Object {
	key: string;
	lastModifiedMillis?: number;
	sizeInBytes?: number;
	etag: string;
}

export interface S3ObjectListing {
	contents: S3Object[];
	truncated: boolean;
}

export interface UploadProgress {
	uploadId: string;
	percentComplete: number;
}

export interface CalibrationRequestCiemss {
	modelConfigId: string;
	extra: any;
	timespan?: TimeSpan;
	dataset: DatasetLocation;
	engine: string;
}

export interface CalibrationRequestJulia {
	modelConfigId: string;
	extra: any;
	timespan?: TimeSpan;
	dataset: DatasetLocation;
	engine: string;
}

export interface CiemssStatusUpdate {
	loss: number;
	progress: number;
	jobId: string;
}

export interface EnsembleCalibrationCiemssRequest {
	modelConfigs: EnsembleModelConfigs[];
	dataset: DatasetLocation;
	timespan: TimeSpan;
	extra: any;
	engine: string;
}

export interface EnsembleSimulationCiemssRequest {
	modelConfigs: EnsembleModelConfigs[];
	timespan: TimeSpan;
	extra: any;
	engine: string;
}

export interface OptimizeRequestCiemss {
	modelConfigId: string;
	timespan: TimeSpan;
	interventions?: OptimizedIntervention;
	stepSize?: number;
	qoi: OptimizeQoi;
	riskBound: number;
	initialGuessInterventions: number[];
	boundsInterventions: number[][];
	extra: OptimizeExtra;
	engine: string;
	userId: string;
}

export interface ScimlStatusUpdate {
	loss: number;
	iter: number;
	params: { [index: string]: number };
	id: string;
	solData: { [index: string]: any };
	timesteps: number[];
}

export interface SimulationRequest {
	modelConfigId: string;
	timespan: TimeSpan;
	extra: any;
	engine: string;
	projectId: string;
	interventions?: Intervention[];
}

export interface DatasetLocation {
	id: string;
	filename: string;
	mappings?: any;
}

export interface EnsembleModelConfigs {
	id: string;
	solutionMappings: { [index: string]: string };
	weight: number;
}

export interface Intervention {
	name: string;
	timestep: number;
	value: number;
}

export interface OptimizeExtra {
	numSamples: number;
	inferredParameters?: string;
	maxiter?: number;
	maxfeval?: number;
	isMinimized?: boolean;
	alpha?: number;
	solverMethod?: string;
}

export interface OptimizeQoi {
	contexts: string[];
	method: string;
}

export interface OptimizedIntervention {
	selection: string;
	paramNames: string[];
	paramValues?: number[];
	startTime?: number[];
}

export interface TimeSpan {
	start: number;
	end: number;
}

export interface TaskResponse {
	id: string;
	script: string;
	status: TaskStatus;
	output: any;
	userId: string;
	additionalProperties: any;
	stdout: string;
	stderr: string;
}

export interface Annotation {
	id: string;
	timestampMillis: number;
	projectId: string;
	content: string;
	userId: string;
	artifactId: string;
	artifactType: string;
	section: string;
}

export interface Event {
	id?: string;
	timestampMillis?: number;
	projectId?: string;
	userId?: string;
	type: EventType;
	value?: string;
}

export interface UserEvent {
	type: EventType;
	user: UserOld;
	id: string;
	message: any;
}

export interface Role {
	id: number;
	name: string;
	description: string;
	authorities: AuthorityInstance[];
	inherited: boolean;
}

export interface Links {
	html: string;
	git: string;
	self: string;
}

export interface Document {
	gddId: string;
	title: string;
	abstractText: string;
	journal: string;
	type: string;
	number: string;
	pages: string;
	publisher: string;
	volume: string;
	year: string;
	link: { [index: string]: string }[];
	author: { [index: string]: string }[];
	identifier: { [index: string]: string }[];
	githubUrls: string[];
	knownTerms: { [index: string]: string[] };
	highlight: string[];
	relatedDocuments: Document[];
	relatedExtractions: Extraction[];
	knownEntities: KnownEntities;
	knownEntitiesCounts: KnownEntitiesCounts;
	citationList: { [index: string]: string }[];
	citedBy: { [index: string]: any }[];
}

export interface DocumentExtraction {
	fileName: string;
	assetType: ExtractionAssetType;
	metadata: { [index: string]: any };
}

export interface ModelHeader {
	name: string;
	description: string;
	schema: string;
	schema_name?: string;
	model_version?: string;
	extracted_from?: string;
}

export interface ModelSemantics {
	ode: OdeSemantics;
	span?: any[];
	typing?: any;
}

export interface ModelMetadata {
	annotations?: Annotations;
	attributes?: any[];
	initials?: { [index: string]: any };
	parameters?: { [index: string]: any };
	card?: Card;
	provenance?: string[];
	source?: any;
	processed_at?: number;
	processed_by?: string;
	variable_statements?: VariableStatement[];
	gollmCard?: any;
	gollmExtractions?: any;
	templateCard?: any;
	code_id?: string;
}

export interface TerariumAssetThatSupportsAdditionalProperties extends TerariumAsset {
}

export interface ModelGrounding {
	identifiers: { [index: string]: any };
	context?: { [index: string]: any };
	modifiers?: any;
}

export interface ModelUnit {
	expression: string;
	expression_mathml: string;
}

export interface Properties {
	name: string;
	grounding?: ModelGrounding;
	description?: string;
}

export interface PetriNetState {
	id: string;
	name: string;
	grounding: ModelGrounding;
	initial: ModelExpression;
}

export interface PetriNetTransition {
	id: string;
	input: string[];
	output: string[];
	grounding?: ModelGrounding;
	properties: PetriNetTransitionProperties;
}

export interface ProvenanceNode {
	id: string;
	type: ProvenanceType;
	uuid: string;
}

export interface ProvenanceEdge {
	relationType: ProvenanceRelationType;
	left: ProvenanceNode;
	right: ProvenanceNode;
}

export interface ModelDistribution {
	type: string;
	parameters: { [index: string]: any };
}

export interface XDDFacetsItemResponse {
	buckets: XDDFacetBucket[];
	doc_count_error_upper_bound: number;
	sum_other_doc_count: number;
}

export interface XDDResponseOK {
	v: number;
	license: string;
}

export interface PermissionRole {
	id: string;
	name: string;
	users: PermissionUser[];
}

export interface UserOld {
	username: string;
	roles: string[];
}

export interface AuthorityInstance {
	id: number;
	mask: number;
	authority: Authority;
}

export interface Extraction {
	id: number;
	askemClass: string;
	properties: ExtractionProperties;
	askemId: string;
	xddCreated: Date;
	xddRegistrant: number;
	highlight: string[];
}

export interface KnownEntities {
	urlExtractions: XDDUrlExtraction[];
	askemObjects: Extraction[];
	summaries: any[];
}

export interface KnownEntitiesCounts {
	askemObjectCount: number;
	urlExtractionCount: number;
}

export interface OdeSemantics {
	rates: Rate[];
	initials?: Initial[];
	parameters?: ModelParameter[];
	observables?: Observable[];
	time?: any;
}

export interface Annotations {
	license?: string;
	authors?: string[];
	references?: string[];
	locations?: string[];
	pathogens?: string[];
	diseases?: string[];
	hosts?: string[];
	time_scale?: string;
	time_start?: string;
	time_end?: string;
	model_types?: string[];
}

export interface Card {
	description?: string;
	authorInst?: string;
	authorAuthor?: string;
	authorEmail?: string;
	date?: string;
	schema?: string;
	provenance?: string;
	dataset?: string;
	complexity?: string;
	usage?: string;
	license?: string;
	assumptions?: string;
	strengths?: string;
}

export interface VariableStatement {
	id: string;
	variable: Variable;
	value?: StatementValue;
	metadata?: VariableStatementMetadata[];
	provenance?: ProvenanceInfo;
}

export interface ModelExpression {
	expression: string;
	expression_mathml: string;
}

export interface PetriNetTransitionProperties {
	name: string;
	description: string;
	grounding?: ModelGrounding;
}

export interface XDDFacetBucket {
	key: string;
	docCount: string;
}

export interface Authority {
	id: number;
	name: string;
	description: string;
}

export interface ExtractionProperties {
	title: string;
	trustScore: string;
	abstractText: string;
	xddId: string;
	documentId: string;
	documentTitle: string;
	contentText: string;
	indexInDocument: number;
	contentJSON: any;
	image: string;
	relevantSentences: string;
	sectionID: string;
	sectionTitle: string;
	caption: string;
	documentBibjson: Document;
	doi: string;
}

export interface XDDUrlExtraction {
	url: string;
	resourceTitle: string;
	extractedFrom: string[];
}

export interface Rate {
	target: string;
	expression: string;
	expression_mathml?: string;
}

export interface Initial {
	target: string;
	expression: string;
	expression_mathml: string;
}

export interface ModelParameter {
	id: string;
	name?: string;
	description?: string;
	value?: number;
	grounding?: ModelGrounding;
	distribution?: ModelDistribution;
	units?: ModelUnit;
}

export interface Observable {
	id: string;
	name?: string;
	states?: string[];
	expression?: string;
	expression_mathml?: string;
}

export interface Variable {
	id: string;
	name: string;
	metadata: VariableMetadata[];
	column: DataColumn[];
	paper: Paper;
	equations: EquationVariable[];
	dkg_groundings: DKGConcept[];
}

export interface StatementValue {
	value: string;
	type: string;
	dkg_grounding?: DKGConcept;
}

export interface VariableStatementMetadata {
	type: string;
	value: string;
}

export interface ProvenanceInfo {
	method: string;
	description: string;
}

export interface VariableMetadata {
	type: string;
	value: string;
}

export interface DataColumn {
	id: string;
	name: string;
	dataset: MetadataDataset;
}

export interface Paper {
	id: string;
	doi: string;
	file_directory: string;
}

export interface EquationVariable {
	id: string;
	text: string;
	image: string;
}

export interface DKGConcept {
	id: string;
	name: string;
	score: number;
}

export interface MetadataDataset {
	id: string;
	name: string;
	metadata: string;
}

export enum EventType {
	Search = "SEARCH",
	EvaluationScenario = "EVALUATION_SCENARIO",
	RouteTiming = "ROUTE_TIMING",
	ProxyTiming = "PROXY_TIMING",
	AddResourcesToProject = "ADD_RESOURCES_TO_PROJECT",
	ExtractModel = "EXTRACT_MODEL",
	PersistModel = "PERSIST_MODEL",
	TransformPrompt = "TRANSFORM_PROMPT",
	AddCodeCell = "ADD_CODE_CELL",
	RunSimulation = "RUN_SIMULATION",
	RunCalibrate = "RUN_CALIBRATE",
	GithubImport = "GITHUB_IMPORT",
	OperatorDrilldownTiming = "OPERATOR_DRILLDOWN_TIMING",
	TestType = "TEST_TYPE",
}

export enum AuthorityLevel {
	Read = "READ",
	Create = "CREATE",
	Update = "UPDATE",
	Delete = "DELETE",
}

export enum AuthorityType {
	GrantAuthority = "GRANT_AUTHORITY",
	Users = "USERS",
}

export enum RoleType {
	Admin = "ADMIN",
	User = "USER",
	Group = "GROUP",
	Test = "TEST",
	Service = "SERVICE",
	Special = "SPECIAL",
}

export enum AssetType {
	Workflow = "workflow",
	Model = "model",
	Dataset = "dataset",
	Simulation = "simulation",
	Document = "document",
	Code = "code",
	ModelConfiguration = "model-configuration",
	Artifact = "artifact",
	Publication = "publication",
	NotebookSession = "notebook-session",
}

export enum EvaluationScenarioStatus {
	Started = "STARTED",
	Stopped = "STOPPED",
}

export enum TaskStatus {
	Queued = "QUEUED",
	Running = "RUNNING",
	Success = "SUCCESS",
	Failed = "FAILED",
	Cancelling = "CANCELLING",
	Cancelled = "CANCELLED",
}

export enum ClientEventType {
	Heartbeat = "HEARTBEAT",
	Notification = "NOTIFICATION",
	SimulationSciml = "SIMULATION_SCIML",
	SimulationPyciemss = "SIMULATION_PYCIEMSS",
	FileUploadProgress = "FILE_UPLOAD_PROGRESS",
	Extraction = "EXTRACTION",
	ExtractionPdf = "EXTRACTION_PDF",
}

export enum FileType {
	File = "file",
	Dir = "dir",
	Symlink = "symlink",
	Submodule = "submodule",
}

export enum FileCategory {
	Directory = "Directory",
	Code = "Code",
	Data = "Data",
	Documents = "Documents",
	Other = "Other",
}

export enum ProgrammingLanguage {
	Python = "python",
	R = "r",
	Julia = "julia",
	Zip = "zip",
}

export enum TaggableType {
	Datasets = "DATASETS",
	Features = "FEATURES",
	Intermediates = "INTERMEDIATES",
	ModelParameters = "MODEL_PARAMETERS",
	Models = "MODELS",
	Projects = "PROJECTS",
	Publications = "PUBLICATIONS",
	Qualifiers = "QUALIFIERS",
	SimulationParameters = "SIMULATION_PARAMETERS",
	SimulationPlans = "SIMULATION_PLANS",
	SimulationRuns = "SIMULATION_RUNS",
}

export enum OntologicalField {
	Object = "OBJECT",
	Unit = "UNIT",
}

export enum ColumnType {
	Unknown = "UNKNOWN",
	Boolean = "BOOLEAN",
	String = "STRING",
	Char = "CHAR",
	Integer = "INTEGER",
	Int = "INT",
	Float = "FLOAT",
	Double = "DOUBLE",
	Timestamp = "TIMESTAMP",
	Datetime = "DATETIME",
	Date = "DATE",
	Time = "TIME",
}

export enum ProvenanceRelationType {
	BeginsAt = "BEGINS_AT",
	Cites = "CITES",
	CombinedFrom = "COMBINED_FROM",
	Contains = "CONTAINS",
	CopiedFrom = "COPIED_FROM",
	DecomposedFrom = "DECOMPOSED_FROM",
	DerivedFrom = "DERIVED_FROM",
	EditedFrom = "EDITED_FROM",
	EquivalentOf = "EQUIVALENT_OF",
	ExtractedFrom = "EXTRACTED_FROM",
	GeneratedBy = "GENERATED_BY",
	GluedFrom = "GLUED_FROM",
	IsConceptOf = "IS_CONCEPT_OF",
	ParameterOf = "PARAMETER_OF",
	Reinterprets = "REINTERPRETS",
	StratifiedFrom = "STRATIFIED_FROM",
	Uses = "USES",
}

export enum ProvenanceType {
	Concept = "Concept",
	Dataset = "Dataset",
	Model = "Model",
	ModelRevision = "ModelRevision",
	ModelConfiguration = "ModelConfiguration",
	Project = "Project",
	Publication = "Publication",
	Simulation = "Simulation",
	SimulationRun = "SimulationRun",
	Plan = "Plan",
	Artifact = "Artifact",
	Code = "Code",
	Document = "Document",
	Workflow = "Workflow",
	Equation = "Equation",
}

export enum SimulationType {
	Ensemble = "ENSEMBLE",
	Simulation = "SIMULATION",
	Calibration = "CALIBRATION",
	Optimization = "OPTIMIZATION",
	Validation = "VALIDATION",
}

export enum ProgressState {
	Cancelled = "CANCELLED",
	Complete = "COMPLETE",
	Error = "ERROR",
	Failed = "FAILED",
	Queued = "QUEUED",
	Retrieving = "RETRIEVING",
	Running = "RUNNING",
}

export enum SimulationEngine {
	Sciml = "SCIML",
	Ciemss = "CIEMSS",
}

export enum ExtractionAssetType {
	Figure = "FIGURE",
	Table = "TABLE",
	Equation = "EQUATION",
}
