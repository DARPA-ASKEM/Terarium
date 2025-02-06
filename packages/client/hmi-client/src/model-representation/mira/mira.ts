import _, { isEmpty, cloneDeep, uniq } from 'lodash';
import { IGraph } from '@graph-scaffolder/types';
import { NodeType } from '@/services/graph';
import type { ModelConfiguration } from '@/types/Types';
import {
	extractOutcomeControllersMatrix,
	extractSubjectControllersMatrix,
	extractSubjectOutcomeMatrix,
	removeModifiers
} from './mira-util';
import type {
	MiraMatrix,
	MiraMatrixEntry,
	MiraModel,
	MiraTemplate,
	MiraTemplateParams,
	TemplateSummary,
	ObservableSummary
} from './mira-common';
import type { NodeData } from '../petrinet/petrinet-renderer';

export const emptyMiraModel = () => {
	const newModel: MiraModel = {
		templates: [],
		parameters: {},
		initials: {},
		observables: {},
		annotations: {},
		time: {}
	};
	return newModel;
};

/**
 * Collection of MMT related functions
 * */
export const getContext = (miraModel: MiraModel) => {
	const modifierKeys = new Set<string>();
	const modifierValues = new Set<string>();

	// Heuristics to avoid picking up wrong stuff
	// - if the modifier value starts with 'ncit:' then it is not a user initiated stratification
	// const modifiers = rawModifiers.filter((v) => {
	// 	if (v.startsWith('ncit:')) return false;
	// 	return true;
	// });
	const addWithGuard = (k: string, v: string) => {
		if (v.startsWith('ncit:')) return;
		modifierKeys.add(k);
		modifierValues.add(v);
	};

	miraModel.templates.forEach((t) => {
		if (t.subject?.context) {
			Object.entries(t.subject.context).forEach(([k, v]) => {
				addWithGuard(k, v as string);
			});
		}
		if (t.outcome?.context) {
			Object.entries(t.outcome.context).forEach(([k, v]) => {
				addWithGuard(k, v as string);
			});
		}
		if (t.controller?.context) {
			Object.entries(t.controller.context).forEach(([k, v]) => {
				addWithGuard(k, v as string);
			});
		}
		if (t.controllers && t.controllers.length > 0) {
			t.controllers.forEach((miraConcept) => {
				Object.entries(miraConcept.context).forEach(([k, v]) => {
					addWithGuard(k, v as string);
				});
			});
		}
	});

	return {
		keys: [...modifierKeys],
		values: [...modifierValues]
	};
};

export const isStratifiedModel = (miraModel: MiraModel) => {
	const keys = getContext(miraModel).keys;
	return keys.length > 0;
};

/**
 * Collapse parameters into a lookup map:
 *
 * { var1 => [var1_a, var1_b ...] }
 *
 * Note this is mostly heuristically based, so there may be some edge cases where this will not work,
 * expecially if the model is changed manually after a stratification operation, e.g. rename a stratified variable
 * */
export const collapseParameters = (miraModel: MiraModel, miraTemplateParams: MiraTemplateParams) => {
	const map = new Map<string, string[]>();
	const keys = Object.keys(miraModel.parameters);
	const contextValues = getContext(miraModel).values;

	for (let i = 0; i < keys.length; i++) {
		const key = keys[i];
		const name = key;
		const tokens = key.split('_') as string[];
		const rootName = _.first(tokens) as string;

		// There are some cases where parameter names have underscores but are not stratified
		const displayName = miraModel.parameters[key].display_name;
		if (tokens.length > 1 && displayName === key) {
			map.set(name, [name]);
			continue;
		}

		const nameContainsContext = _.intersection(tokens, contextValues).length > 0;
		const hasNumericSuffixes =
			tokens.length > 1 &&
			tokens.every((d, idx) => {
				if (idx === 0) return true;
				return !Number.isNaN(+d);
			});

		if (!nameContainsContext && !hasNumericSuffixes) {
			map.set(name, [name]);
			continue;
		}

		if (map.has(rootName)) {
			map.get(rootName)?.push(name);
		} else {
			map.set(rootName, [name]);
		}
	}

	let mapKeys = [...map.keys()];
	const templateParams = Object.values(miraTemplateParams);

	/**
	 * Assume that a stratified parameters do not intermingle. E.g.
	 * if we stratify age into [a1, a2, a3, ... a8], an expression
	 * will only have 1 of the new age-strata-variable. If it is not the
	 * case we will re-classify them.
	 * */
	for (let i = 0; i < mapKeys.length; i++) {
		const mapKey = mapKeys[i];
		const childrenParams = map.get(mapKey);

		const isNormalStratify = _.every(templateParams, (t) => _.intersection(t.params, childrenParams).length <= 1);
		if (!isNormalStratify) {
			map.delete(mapKey);
			childrenParams?.forEach((p) => {
				map.set(p, [p]);
			});
		}
	}

	// when a key only has one child, we will rename the key of the root to the child.
	// this is to avoid renaming when a single entry key has an underscore
	mapKeys = [...map.keys()];
	[...map.keys()].forEach((key) => {
		const newKey = map.get(key)?.[0];
		if (newKey && map.get(key)?.length === 1 && newKey !== key) {
			map.set(newKey, [newKey]);
			map.delete(key);
		}
	});

	return map;
};

export const collapseInitials = (miraModel: MiraModel) => {
	const map = new Map<string, string[]>();
	const keys = Object.keys(miraModel.initials);

	for (let i = 0; i < keys.length; i++) {
		const key = keys[i];
		const name = key;
		const rootName = _.first(key.split('_')) as string;

		if (map.has(rootName)) {
			map.get(rootName)?.push(name);
		} else {
			map.set(rootName, [name]);
		}
	}

	// when a key only has one child, we will rename the key of the root to the child.
	// this is to avoid renaming when a single entry key has an underscore
	[...map.keys()].forEach((key) => {
		const newKey = map.get(key)?.[0];
		if (newKey && map.get(key)?.length === 1 && newKey !== key) {
			map.set(newKey, [newKey]);
			map.delete(key);
		}
	});

	return map;
};

export const rawTemplatesSummary = (miraModel: MiraModel) => {
	const allTemplates: TemplateSummary[] = [];
	miraModel.templates.forEach((t) => {
		const summary: TemplateSummary = {
			name: t.name,
			expression: t.rate_law,
			subject: '',
			outcome: '',
			controllers: []
		};

		if (t.subject) {
			summary.subject = t.subject.name;
		}
		if (t.outcome) {
			summary.outcome = t.outcome.name;
		}

		// note controller and controllers are mutually exclusive
		if (t.controller) {
			summary.controllers.push(t.controller.name);
		}
		if (t.controllers && t.controllers.length > 0) {
			t.controllers.forEach((miraConcept) => {
				summary.controllers.push(miraConcept.name);
			});
			t.controllers.sort();
		}
		allTemplates.push(summary);
	});
	return allTemplates;
};

const generateKey = (t: TemplateSummary) => {
	if (t.name.split('_').length > 1) {
		return `${t.name.split('_')[0]}:${t.subject}:${t.outcome}:${t.controllers.join('-')}`;
	}
	return `${t.subject}:${t.outcome}:${t.controllers.join('-')}`;
};
export const collapseTemplates = (miraModel: MiraModel) => {
	const allTemplates: TemplateSummary[] = [];
	const uniqueTemplates: TemplateSummary[] = [];
	const scrubbingKeys = getContext(miraModel).keys;

	// 1. Roll back to "original name" by trimming off modifiers
	miraModel.templates.forEach((t) => {
		const scrubbedTemplate: TemplateSummary = {
			name: t.name,
			expression: t.rate_law,
			subject: '',
			outcome: '',
			controllers: []
		};

		if (t.subject) {
			scrubbedTemplate.subject = removeModifiers(t.subject.name, t.subject.context, scrubbingKeys);
		}
		if (t.outcome) {
			scrubbedTemplate.outcome = removeModifiers(t.outcome.name, t.outcome.context, scrubbingKeys);
		}

		// note controller and controllers are mutually exclusive
		if (t.controller) {
			scrubbedTemplate.controllers.push(removeModifiers(t.controller.name, t.controller.context, scrubbingKeys));
		}
		if (t.controllers && t.controllers.length > 0) {
			t.controllers.forEach((miraConcept) => {
				scrubbedTemplate.controllers.push(removeModifiers(miraConcept.name, miraConcept.context, scrubbingKeys));
			});
			t.controllers.sort();
		}
		allTemplates.push(scrubbedTemplate);
	});

	const templateMap = new Map<string, MiraTemplate>();
	miraModel.templates.forEach((t) => {
		templateMap.set(t.name, t);
	});

	// 2. Do post processing
	// - Reduce down to the unique templates-summary, there are used to render the graph
	// - Link unique template-summary back to the original MiraTemplates, this is for interaction
	let keyCounter = 0;
	const check = new Map<string, number>();
	const tempMatrixMap = new Map<string, MiraTemplate[]>();
	const matrixMap = new Map<string, MiraTemplate[]>();

	allTemplates.forEach((t) => {
		const key = generateKey(t);
		if (!tempMatrixMap.has(key)) {
			tempMatrixMap.set(key, []);
		}

		const originalTemplate = templateMap.get(t.name);
		tempMatrixMap.get(key)?.push(originalTemplate as MiraTemplate);

		if (check.has(key)) return;

		uniqueTemplates.push(t);
		check.set(key, keyCounter);
		keyCounter++;
	});

	// 3 Rename and sanitize everything
	uniqueTemplates.forEach((t) => {
		const key = generateKey(t);
		t.name = `group-${check.get(key)}`;
	});
	tempMatrixMap.forEach((value, key) => {
		const name = `group-${check.get(key)}`;
		matrixMap.set(name, value);
	});

	return {
		templatesSummary: uniqueTemplates,
		matrixMap
	};
};

export const collapseObservableReferences = (observableSummary: ObservableSummary, initials: Map<string, string[]>) => {
	const collapsedObservableSummary: ObservableSummary = cloneDeep(observableSummary);
	Object.values(collapsedObservableSummary).forEach((observable) => {
		// Extract the first part of the reference before the first underscore
		observable.references = uniq(
			observable.references.map((r) => {
				const splitReference = r.split('_')[0];
				return initials.has(splitReference) ? splitReference : r;
			})
		);
	});
	return collapsedObservableSummary;
};

export const createInitialMatrix = (miraModel: MiraModel, key: string) => {
	const initialsMap = collapseInitials(miraModel);
	const childrenInitials = initialsMap.get(key);

	const m2: MiraMatrix = [];
	childrenInitials?.forEach((name, idx) => {
		const row: MiraMatrixEntry[] = [];
		row.push({
			row: idx,
			col: 0,
			rowCriteria: name,
			colCriteria: '',
			content: {
				id: name,
				value: miraModel.initials[name].expression
			}
		});
		m2.push(row);
	});
	return m2;
};

/**
 * Assumes one-to-one with cells
 *
 * */
export const createParameterMatrix = (miraModel: MiraModel, miraTemplateParams: MiraTemplateParams, param: string) => {
	const paramMap = collapseParameters(miraModel, miraTemplateParams);
	const childrenParams = paramMap.get(param);
	if (!childrenParams) throw new Error(`Cannot map ${param}`);

	// Create map for mapping params to row/col of matrix
	//   param => [ [subject, outcome, controllers], [subject, outcome, controllers] ... ]
	const paramLocationMap = new Map<string, TemplateSummary[]>();
	const templateParams = Object.values(miraTemplateParams);
	templateParams.forEach((templateParam) => {
		const params = templateParam.params;
		params.forEach((paramName) => {
			if (!paramLocationMap.has(paramName)) paramLocationMap.set(paramName, []);

			paramLocationMap.get(paramName)?.push({
				name: templateParam.name,
				subject: templateParam.subject,
				outcome: templateParam.outcome,
				controllers: templateParam.controllers
			});
		});
	});

	// Create map for param values
	//   param => value
	const paramValueMap = new Map<string, any>();
	Object.values(miraModel.parameters).forEach((paramObj) => {
		paramValueMap.set(paramObj.name, paramObj.value);
	});

	// Find templates with expressions that contains one or more of the params
	const templates = miraModel.templates.filter((t) => {
		const miraTemplateParam = miraTemplateParams[t.name];
		const intersection = _.intersection(childrenParams, miraTemplateParam.params);
		return intersection.length > 0;
	});

	const subjectOutcome = extractSubjectOutcomeMatrix(templates, childrenParams, paramValueMap, paramLocationMap);
	const subjectControllers = extractSubjectControllersMatrix(
		templates,
		childrenParams,
		paramValueMap,
		paramLocationMap
	);
	const outcomeControllers = extractOutcomeControllersMatrix(
		templates,
		childrenParams,
		paramValueMap,
		paramLocationMap
	);

	// Others, may be initial, maybe no in use ...
	const other: MiraMatrix = [];
	childrenParams.forEach((name, idx) => {
		const row: MiraMatrixEntry[] = [];
		row.push({
			row: idx,
			col: 0,
			rowCriteria: name,
			colCriteria: '',
			content: {
				id: name,
				value: miraModel.parameters[name].value
			}
		});
		other.push(row);
	});

	return {
		subjectOutcome,
		subjectControllers,
		outcomeControllers,
		other
	};
};

export const convertToIGraph = (
	miraModel: MiraModel,
	initObservableSummary: ObservableSummary,
	isStratified: boolean
) => {
	const templates: TemplateSummary[] = [];
	let observableSummary: ObservableSummary = {};

	if (isStratified) {
		templates.push(...collapseTemplates(miraModel).templatesSummary);
		observableSummary = collapseObservableReferences(initObservableSummary, collapseInitials(miraModel));
	} else {
		templates.push(...rawTemplatesSummary(miraModel));
		observableSummary = cloneDeep(initObservableSummary);
	}

	const graph: IGraph<any, any> = {
		nodes: [],
		edges: [],
		width: 500,
		height: 500,
		amr: null
	};

	const subjects = new Set<string>(templates.map((d) => d.subject));
	const outcomes = new Set<string>(templates.map((d) => d.outcome));
	// const nodeNames = [...new Set([...subjects, ...outcomes])];

	const controllers = new Set<string>(templates.map((d) => d.controllers).flat());
	const nodeNames = [...new Set([...subjects, ...outcomes, ...controllers])];

	// concepts
	nodeNames.forEach((nodeName) => {
		if (nodeName === '') return;
		graph.nodes.push({
			id: nodeName,
			label: nodeName,
			x: 0,
			y: 0,
			width: 50,
			height: 50,
			data: { type: NodeType.State },
			nodes: []
		});
	});

	// templates
	templates.forEach((t) => {
		const nodeData: NodeData = {
			type: NodeType.Transition,
			expression: t.expression
		};

		graph.nodes.push({
			id: t.name,
			label: '',
			x: 0,
			y: 0,
			width: 50,
			height: 50,
			data: nodeData,
			nodes: []
		});
	});

	// edges
	templates.forEach((t) => {
		if (t.subject !== '') {
			graph.edges.push({
				source: t.subject,
				target: t.name,
				points: [],
				data: {}
			});
		}
		if (t.outcome !== '') {
			graph.edges.push({
				source: t.name,
				target: t.outcome,
				points: [],
				data: {}
			});
		}
		if (t.controllers && t.controllers.length > 0) {
			t.controllers.forEach((controllerName) => {
				graph.edges.push({
					source: controllerName,
					target: t.name,
					points: [],
					data: { isController: true }
				});
			});
		}
	});

	// observables
	if (!isEmpty(observableSummary)) {
		Object.keys(observableSummary).forEach((key) => {
			const observable = observableSummary[key];
			graph.nodes.push({
				id: key,
				label: observable.name,
				x: 0,
				y: 0,
				width: 50,
				height: 50,
				data: {
					type: NodeType.Observable,
					expression: observable.expression,
					references: observable.references
				},
				nodes: []
			});

			observable.references.forEach((reference: string) => {
				graph.edges.push({
					id: observable.expression,
					source: reference,
					target: key,
					points: [],
					data: { isObservable: true }
				});
			});
		});
	}

	return graph;
};

// Experimental, additional matrix context for LLM agent
// to match datasets to model parameters
export const generateModelDatasetConfigurationContext = (mmt: MiraModel, miraTemplateParams: MiraTemplateParams) => {
	// Create all possible matrices
	const rootParams = collapseParameters(mmt, miraTemplateParams);
	const rootParamKeys = [...rootParams.keys()];

	const lines: any[] = [];
	rootParamKeys.forEach((key) => {
		const matrices = createParameterMatrix(mmt, miraTemplateParams, key);
		let header = '';

		const subjectOutcome = matrices.subjectOutcome;
		const subjectControllers = matrices.subjectControllers;
		const outcomeControllers = matrices.outcomeControllers;

		if (subjectOutcome.matrix.length > 0) {
			lines.push(`subject-outcome of ${key}`);
			header = `,${subjectOutcome.rowNames.join(',')}`;
			lines.push('');
			lines.push(header);
			subjectOutcome.matrix.forEach((r, idx) => {
				const rowStr = `${subjectOutcome.colNames[idx]},${r.map((d) => d.content.id).join(',')}`;
				lines.push(rowStr);
			});
			lines.push('');
			lines.push('');
		}

		if (subjectControllers.matrix.length > 0) {
			lines.push(`subject-controllers of ${key}`);
			header = `,${subjectControllers.rowNames.join(',')}`;
			lines.push('');
			lines.push(header);
			subjectControllers.matrix.forEach((r, idx) => {
				const rowStr = `${subjectControllers.colNames[idx]},${r.map((d) => d.content.id).join(',')}`;
				lines.push(rowStr);
			});
			lines.push('');
			lines.push('');
		}

		if (outcomeControllers.matrix.length > 0) {
			lines.push(`outcome-controllers of ${key}`);
			header = `,${outcomeControllers.rowNames.join(',')}`;
			lines.push('');
			lines.push(header);
			outcomeControllers.matrix.forEach((r, idx) => {
				const rowStr = `${outcomeControllers.colNames[idx]},${r.map((d) => d.content.id).join(',')}`;
				lines.push(rowStr);
			});
			lines.push('');
			lines.push('');
		}
	});

	// Make string
	return lines.join('\n');
};

export function makeConfiguredMMT(mmt: MiraModel, modelConfiguration: ModelConfiguration): MiraModel {
	const mmtCopy = cloneDeep(mmt);
	modelConfiguration.initialSemanticList.forEach((initial) => {
		const mmtInitial = mmtCopy.initials[initial.target];
		if (mmtInitial) {
			mmtInitial.expression = initial.expression;
		}
	});
	modelConfiguration.parameterSemanticList.forEach((parameter) => {
		const mmtParameter = mmtCopy.parameters[parameter.referenceId];
		if (mmtParameter) {
			mmtParameter.value = parameter.distribution.parameters.value;
		}
	});
	modelConfiguration.observableSemanticList.forEach((observable) => {
		const mmtObservable = mmtCopy.observables[observable.referenceId];
		if (mmtObservable) {
			mmtObservable.expression = observable.expression;
		}
	});
	return mmtCopy;
}
