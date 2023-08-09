export const amr = {
	name: 'SIR model',
	schema:
		'https://raw.githubusercontent.com/DARPA-ASKEM/Model-Representations/petrinet_v0.5/petrinet/petrinet_schema.json',
	schema_name: 'petrinet',
	description: 'SIR model',
	model_version: '0.1',
	properties: {},
	model: {
		states: [
			{
				id: 'S',
				name: 'S',
				grounding: {
					identifiers: {
						ido: '0000514'
					},
					modifiers: {}
				},
				units: {
					expression: 'person',
					expression_mathml: '<ci>person</ci>'
				}
			},
			{
				id: 'I',
				name: 'I',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {}
				},
				units: {
					expression: 'person',
					expression_mathml: '<ci>person</ci>'
				}
			},
			{
				id: 'R',
				name: 'R',
				grounding: {
					identifiers: {
						ido: '0000592'
					},
					modifiers: {}
				},
				units: {
					expression: 'person',
					expression_mathml: '<ci>person</ci>'
				}
			}
		],
		transitions: [
			{
				id: 't1',
				input: ['I', 'S'],
				output: ['I', 'I'],
				properties: {
					name: 't1'
				}
			},
			{
				id: 't2',
				input: ['I'],
				output: ['R'],
				properties: {
					name: 't2'
				}
			}
		]
	},
	semantics: {
		ode: {
			rates: [
				{
					target: 't1',
					expression: 'I*S*b',
					expression_mathml: '<apply><times/><ci>I</ci><ci>S</ci><ci>b</ci></apply>'
				},
				{
					target: 't2',
					expression: 'I*g',
					expression_mathml: '<apply><times/><ci>I</ci><ci>g</ci></apply>'
				}
			],
			initials: [
				{
					target: 'S',
					expression: '999.000000000000',
					expression_mathml: '<cn>999.0</cn>'
				},
				{
					target: 'I',
					expression: '1.00000000000000',
					expression_mathml: '<cn>1.0</cn>'
				},
				{
					target: 'R',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				}
			],
			parameters: [
				{
					id: 'b',
					value: 0.4,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'g',
					value: 0.09090909090909091,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				}
			],
			observables: [],
			time: {
				id: 't',
				units: {
					expression: 'day',
					expression_mathml: '<ci>day</ci>'
				}
			}
		}
	},
	metadata: {
		annotations: {
			license: null,
			authors: [],
			references: [],
			time_scale: null,
			time_start: null,
			time_end: null,
			locations: [],
			pathogens: [],
			diseases: [],
			hosts: [],
			model_types: []
		}
	}
};
