/* tslint:disable */
/* eslint-disable */

export interface Event {
    id?: string;
    timestampMillis?: number;
    projectId?: number;
    username?: string;
    type: EventType;
    value?: string;
}

export interface CsvAsset {
    csv: string[][];
    stats?: CsvColumnStats[];
    headers: string[];
}

export interface CsvColumnStats {
    bins: number[];
    minValue: number;
    maxValue: number;
    mean: number;
    median: number;
    sd: number;
}

export interface DocumentAsset {
    id?: number;
    title: string;
    xdd_uri: string;
}

export interface ProvenanceQueryParam {
    rootId?: number;
    rootType?: ProvenanceType;
    userId?: number;
}

export enum EventType {
    Search = "SEARCH",
}

export enum ProvenanceType {
    Dataset = "Dataset",
    Intermediate = "Intermediate",
    Model = "Model",
    ModelParameter = "ModelParameter",
    ModelRevision = "ModelRevision",
    Plan = "Plan",
    PlanParameter = "PlanParameter",
    Publication = "Publication",
    Project = "Project",
    Concept = "Concept",
    SimulationRun = "SimulationRun",
}
