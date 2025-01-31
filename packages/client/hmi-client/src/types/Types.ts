/* tslint:disable */
/* eslint-disable */

export interface ClientConfig {
    baseUrl: string;
    documentationUrl: string;
    clientLogShippingEnabled: boolean;
    clientLogShippingIntervalMillis: number;
    sseHeartbeatIntervalMillis: number;
}

export interface ProjectSearchResult {
    projectId?: string;
    score?: number;
    assets?: ProjectSearchResultAsset[];
}

export interface ProjectSearchResultAsset {
    assetId: string;
    assetType: AssetType;
    assetName: string;
    assetShortDescription: string;
    createdOn: Date;
    embeddingContent: string;
    embeddingType: TerariumAssetEmbeddingType;
    score: number;
}

export interface ClientEvent<T> {
    id: string;
    createdAtMs: number;
    type: ClientEventType;
    projectId?: string;
    notificationGroupId?: string;
    userId?: string;
    data: T;
}

export interface ClientLog {
    level: string;
    timestampMillis: number;
    message: string;
    args?: string[];
}

export interface StatusUpdate<T> {
    progress: number;
    state: ProgressState;
    message: string;
    error: string;
    data: T;
}

export interface TerariumAsset extends TerariumEntity {
    name?: string;
    description?: string;
    fileNames?: string[];
    deletedOn?: Date;
    temporary?: boolean;
    publicAsset?: boolean;
}

export interface TerariumEntity {
    id?: string;
    createdOn?: Date;
    updatedOn?: Date;
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
    metadata?: any;
}

export interface ChartAnnotation extends TerariumAsset {
    nodeId: string;
    outputId: string;
    chartId: string;
    layerSpec: any;
    llmGenerated: boolean;
    metadata: any;
}

export interface CsvAsset {
    csv: string[][];
    headers: string[];
    rowCount: number;
}

export interface Grounding extends TerariumEntity {
    identifiers: { [index: string]: string };
    context?: { [index: string]: string };
    modifiers?: { [index: string]: string };
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

export interface Summary extends TerariumAsset {
    generatedSummary?: string;
    humanSummary?: string;
    previousSummary?: string;
}

export interface Code extends TerariumAsset {
    files?: { [index: string]: CodeFile };
    repoUrl?: string;
    metadata?: { [index: string]: string };
}

export interface CodeFile extends TerariumEntity {
    fileName: string;
    dynamics: Dynamics;
    language: ProgrammingLanguage;
}

export interface Dynamics {
    name: string;
    description: string;
    block: string[];
}

export interface Dataset extends TerariumAsset {
    userId?: string;
    esgfId?: string;
    dataSourceDate?: Date;
    datasetUrl?: string;
    datasetUrls?: string[];
    columns?: DatasetColumn[];
    metadata?: any;
    source?: string;
    grounding?: Grounding;
}

export interface DatasetColumn extends TerariumEntity, GroundedSemantic {
    conceptId?: string;
    fileName: string;
    dataType: ColumnType;
    stats?: DatasetColumnStats;
    formatStr?: string;
    annotations: string[];
    metadata?: any;
    dataset?: Dataset;
}

export interface DatasetColumnStats {
    numericStats: NumericColumnStats;
    nonNumericStats: NonNumericColumnStats;
}

export interface DocumentAsset extends TerariumAsset {
    userId?: string;
    documentUrl?: string;
    metadata?: { [index: string]: any };
    source?: string;
    text?: string;
    grounding?: Grounding;
    /**
     * @deprecated
     */
    documentAbstract?: string;
    /**
     * @deprecated
     */
    assets?: DocumentExtraction[];
    extractions?: ExtractedDocumentPage[];
    thumbnail?: any;
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

export interface ModelDescription {
    id: string;
    header: ModelHeader;
    timestamp: Date;
    userId?: string;
}

export interface InferredParameterSemantic extends Semantic {
    referenceId: string;
    distribution: ModelDistribution;
    default: boolean;
}

export interface InitialSemantic extends Semantic {
    target: string;
    expression: string;
    expressionMathml: string;
}

export interface ModelConfiguration extends TerariumAsset {
    modelId: string;
    simulationId?: string;
    temporalContext?: Date;
    extractionDocumentId?: string;
    extractionPage?: number;
    observableSemanticList: ObservableSemantic[];
    parameterSemanticList: ParameterSemantic[];
    initialSemanticList: InitialSemantic[];
    inferredParameterList?: InferredParameterSemantic[];
}

export interface ObservableSemantic extends Semantic {
    referenceId: string;
    states: string[];
    expression: string;
    expressionMathml: string;
}

export interface ParameterSemantic extends Semantic {
    referenceId: string;
    distribution: ModelDistribution;
    default: boolean;
}

export interface Semantic extends TerariumEntity {
    source: string;
    type: SemanticType;
}

export interface Author {
    name: string;
}

export interface State extends GroundedSemantic {
    id: string;
    units?: ModelUnit;
}

export interface Transition extends GroundedSemantic {
    id: string;
    input: string[];
    output: string[];
    expression?: string;
    properties?: Properties;
}

export interface NotebookSession extends TerariumAsset {
    data: any;
}

export interface Project extends TerariumAsset {
    userId: string;
    thumbnail: string;
    userName?: string;
    authors?: string[];
    overviewContent?: any;
    projectAssets: ProjectAsset[];
    metadata?: { [index: string]: string };
    sampleProject?: boolean;
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
    grounding: Grounding;
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
    grounding?: Grounding;
    distribution?: ModelDistribution;
}

export interface RegNetVertex extends GroundedSemantic {
    id: string;
    sign: boolean;
    initial?: any;
    rate_constant?: any;
}

export interface Simulation extends TerariumAsset {
    executionPayload: any;
    resultFiles?: string[];
    type: SimulationType;
    status: ProgressState;
    progress?: number;
    statusMessage?: string;
    startTime?: Date;
    completedTime?: Date;
    engine: SimulationEngine;
    userId?: string;
    projectId?: string;
    updates: SimulationUpdate[];
}

export interface SimulationUpdate extends TerariumEntity {
    data: any;
    simulation: Simulation;
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
    verbosity?: number;
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
    locations?: string[];
}

export interface EntitySimilarityResult {
    source: string;
    target: string;
    similarity: number;
}

export interface NotificationEvent extends TerariumEntity {
    progress: number;
    state: ProgressState;
    acknowledgedOn: Date;
    data: any;
    notificationGroup: NotificationGroup;
}

export interface NotificationGroup extends TerariumEntity {
    userId: string;
    type: string;
    projectId?: string;
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
    policyInterventionId?: string;
    dataset: DatasetLocation;
    engine: string;
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
    optimizeInterventions: OptimizeInterventions[];
    fixedInterventions?: Intervention[];
    loggingStepSize?: number;
    qoi: OptimizeQoi[];
    boundsInterventions: number[][];
    extra: OptimizeExtra;
    engine: string;
    userId: string;
}

export interface SimulationRequest {
    modelConfigId: string;
    timespan: TimeSpan;
    extra: any;
    engine: string;
    policyInterventionId?: string;
}

export interface DynamicIntervention {
    parameter: string;
    threshold: number;
    value: number;
    appliedTo: string;
    type: InterventionSemanticType;
}

export interface Intervention {
    name: string;
    extractionDocumentId?: string;
    extractionDatasetId?: string;
    extractionPage?: number;
    staticInterventions: StaticIntervention[];
    dynamicInterventions: DynamicIntervention[];
}

export interface InterventionPolicy extends TerariumAsset {
    modelId: string;
    interventions: Intervention[];
}

export interface StaticIntervention {
    timestep: number;
    value: number;
    appliedTo: string;
    type: InterventionSemanticType;
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

export interface OptimizeExtra {
    numSamples: number;
    inferredParameters?: string;
    maxiter?: number;
    maxfeval?: number;
    isMinimized?: boolean;
    alpha?: number[];
    solverMethod?: string;
    solverStepSize?: number;
}

export interface OptimizeInterventions {
    interventionType: string;
    paramNames: string[];
    paramValues?: number[];
    startTime?: number[];
    objectiveFunctionOption: string;
    initialGuess?: number[];
    relativeImportance: number;
}

export interface OptimizeQoi {
    contexts: string[];
    method: string;
    riskBound: number;
    isMinimized: boolean;
}

export interface TimeSpan {
    start: number;
    end: number;
}

export interface CiemssCalibrateStatusUpdate extends CiemssStatusUpdate {
    loss: number;
}

export interface CiemssOptimizeStatusUpdate extends CiemssStatusUpdate {
    currentResults: number[];
    totalPossibleIterations: number;
}

export interface CiemssStatusUpdate {
    jobId: string;
    progress: number;
    type: CiemssStatusType;
}

export interface TaskResponse {
    id: string;
    script: string;
    status: TaskStatus;
    output: any;
    userId: string;
    projectId: string;
    additionalProperties: any;
    stdout: string;
    stderr: string;
    requestSHA256: string;
    routingKey: string;
    useCache: boolean;
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

export interface ProjectSearchResponse {
    projectId: string;
    score: number;
    hits: ProjectSearchAsset[];
}

export interface SimulationNotificationData {
    simulationId: string;
    simulationType: SimulationType;
    simulationEngine: SimulationEngine;
    metadata: any;
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

export interface GroundedSemantic {
    name?: string;
    description?: string;
    grounding?: Grounding;
}

export interface NumericColumnStats {
    mean: number;
    median: number;
    min: number;
    max: number;
    quartiles: number[];
    data_type: string;
    std_dev: number;
    unique_values: number;
    missing_values: number;
    histogram_bins: HistogramBin[];
}

export interface NonNumericColumnStats {
    data_type: string;
    unique_values: number;
    most_common: { [index: string]: number };
    missing_values: number;
}

export interface DocumentExtraction {
    fileName: string;
    assetType: ExtractionAssetType;
    metadata: { [index: string]: any };
}

export interface ExtractedDocumentPage {
    pageNumber: number;
    text: string;
    tables: any[];
    equations: any[];
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
    description?: any;
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

export interface ModelDistribution {
    type: string;
    parameters: { [index: string]: any };
}

export interface ModelUnit {
    expression: string;
    expression_mathml: string;
}

export interface Properties {
    name: string;
    grounding?: Grounding;
    description?: string;
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

export interface PermissionRole {
    id: string;
    name: string;
    users: PermissionUser[];
}

export interface UserOld {
    username: string;
    roles: string[];
}

export interface ProjectSearchAsset {
    assetId: string;
    assetType: AssetType;
    embeddingType: TerariumAssetEmbeddingType;
    score: number;
}

export interface AuthorityInstance {
    id: number;
    mask: number;
    authority: Authority;
}

export interface HistogramBin {
    start: number;
    end: number;
    count: number;
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
    authors?: Author[];
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

export interface Authority {
    id: number;
    name: string;
    description: string;
}

export interface Rate {
    target: string;
    description?: string;
    expression: string;
    expression_mathml?: string;
}

export interface Initial {
    target: string;
    description?: string;
    expression: string;
    expression_mathml: string;
}

export interface ModelParameter extends GroundedSemantic {
    id: string;
    value?: number;
    distribution?: ModelDistribution;
    units?: ModelUnit;
}

export interface Observable extends GroundedSemantic {
    id: string;
    states?: string[];
    units?: ModelUnit;
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
    InterventionPolicy = "intervention-policy",
    NotebookSession = "notebook-session",
    Project = "project",
}

export enum EvaluationScenarioStatus {
    Started = "STARTED",
    Stopped = "STOPPED",
}

export enum CiemssStatusType {
    Optimize = "optimize",
    Calibrate = "calibrate",
}

export enum TaskStatus {
    Queued = "QUEUED",
    Running = "RUNNING",
    Success = "SUCCESS",
    Failed = "FAILED",
    Cancelling = "CANCELLING",
    Cancelled = "CANCELLED",
}

export enum TerariumAssetEmbeddingType {
    Overview = "OVERVIEW",
    Name = "NAME",
    Description = "DESCRIPTION",
}

export enum ClientEventType {
    ChartAnnotationCreate = "CHART_ANNOTATION_CREATE",
    ChartAnnotationDelete = "CHART_ANNOTATION_DELETE",
    CloneProject = "CLONE_PROJECT",
    KnowledgeEnrichmentModel = "KNOWLEDGE_ENRICHMENT_MODEL",
    Extraction = "EXTRACTION",
    ExtractionPdf = "EXTRACTION_PDF",
    FileUploadProgress = "FILE_UPLOAD_PROGRESS",
    Heartbeat = "HEARTBEAT",
    Notification = "NOTIFICATION",
    SimulationNotification = "SIMULATION_NOTIFICATION",
    SimulationPyciemss = "SIMULATION_PYCIEMSS",
    TaskExtractTextPdf = "TASK_EXTRACT_TEXT_PDF",
    TaskExtractTablePdf = "TASK_EXTRACT_TABLE_PDF",
    TaskExtractEquationPdf = "TASK_EXTRACT_EQUATION_PDF",
    TaskFunmanValidation = "TASK_FUNMAN_VALIDATION",
    TaskGollmCompareModel = "TASK_GOLLM_COMPARE_MODEL",
    TaskGollmConfigureModelFromDataset = "TASK_GOLLM_CONFIGURE_MODEL_FROM_DATASET",
    TaskGollmConfigureModelFromDocument = "TASK_GOLLM_CONFIGURE_MODEL_FROM_DOCUMENT",
    TaskGollmEnrichAmr = "TASK_GOLLM_ENRICH_AMR",
    TaskGollmEnrichDataset = "TASK_GOLLM_ENRICH_DATASET",
    TaskGollmEquationsFromImage = "TASK_GOLLM_EQUATIONS_FROM_IMAGE",
    TaskGollmGenerateSummary = "TASK_GOLLM_GENERATE_SUMMARY",
    TaskGollmInterventionsFromDocument = "TASK_GOLLM_INTERVENTIONS_FROM_DOCUMENT",
    TaskGollmInterventionsFromDataset = "TASK_GOLLM_INTERVENTIONS_FROM_DATASET",
    TaskGollmModelCard = "TASK_GOLLM_MODEL_CARD",
    TaskGollmDatasetStatistics = "TASK_GOLLM_DATASET_STATISTICS",
    TaskMiraAmrToMmt = "TASK_MIRA_AMR_TO_MMT",
    TaskMiraGenerateModelLatex = "TASK_MIRA_GENERATE_MODEL_LATEX",
    TaskMiraCompareModelsConcepts = "TASK_MIRA_COMPARE_MODELS_CONCEPTS",
    TaskUndefinedEvent = "TASK_UNDEFINED_EVENT",
    WorkflowDelete = "WORKFLOW_DELETE",
    WorkflowUpdate = "WORKFLOW_UPDATE",
}

export enum ProgressState {
    Cancelled = "CANCELLED",
    Complete = "COMPLETE",
    Error = "ERROR",
    Failed = "FAILED",
    Queued = "QUEUED",
    Retrieving = "RETRIEVING",
    Running = "RUNNING",
    Cancelling = "CANCELLING",
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

export enum SemanticType {
    Initial = "initial",
    Parameter = "parameter",
    Observable = "observable",
    Inferred = "inferredParameter",
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
    Simulation = "Simulation",
    SimulationRun = "SimulationRun",
    Plan = "Plan",
    Artifact = "Artifact",
    Code = "Code",
    Document = "Document",
    Workflow = "Workflow",
    Equation = "Equation",
    InterventionPolicy = "InterventionPolicy",
}

export enum SimulationType {
    Ensemble = "ENSEMBLE",
    Simulation = "SIMULATION",
    Calibration = "CALIBRATION",
    Optimization = "OPTIMIZATION",
    Validation = "VALIDATION",
}

export enum SimulationEngine {
    Ciemss = "CIEMSS",
}

export enum InterventionSemanticType {
    State = "state",
    Parameter = "parameter",
}

export enum ExtractionAssetType {
    Figure = "FIGURE",
    Table = "TABLE",
    Equation = "EQUATION",
}
