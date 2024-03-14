export interface MiraConcept {
	name: string;
	display_name: string | null;
	description: string | null;
	identifiers: any;
	context: any;
	units: any;
}

export interface MiraParameter {
	name: string;
	display_name: string | null;
	description: string | null;
	identifiers: any;
	context: any;
	units: any;
	value: any;
	distribution: any;
}

export interface MiraTemplate {
	rate_law: string;
	name: string;
	display_name: string | null;
	type: string;
	controller?: MiraConcept;
	controllers?: MiraConcept[];
	subject: MiraConcept;
	outcome: MiraConcept;
	provenance: any[];
}

export interface MiraModel {
	templates: MiraTemplate[];
	parameters: { [key: string]: MiraParameter };
	initials: { [key: string]: any };
	observables: { [key: string]: any };
	annotations: any;
	time: any;
}

export interface MiraTemplateParams {
	[key: string]: {
		name: string;
		params: string[];
		subject: string;
		outcome: string;
		controllers: string[];
	};
}

// Terarium MIRA types
export type MiraMatrixEntry = { id: any; value: any };
export type MiraMatrix = MiraMatrixEntry[][];

export interface TemplateSummary {
	name: string;
	subject: string;
	outcome: string;
	controllers: string[];
}
