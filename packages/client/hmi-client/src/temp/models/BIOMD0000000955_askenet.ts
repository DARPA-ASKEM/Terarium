// @ts-nocheck
/* eslint-disable */
import { Model } from '@/types/Types';

export const SIDARTHE: Model = {
	name: 'Giordano2020 - SIDARTHE model of COVID-19 spread in Italy',
	schema:
		'https://raw.githubusercontent.com/DARPA-ASKEM/Model-Representations/petrinet_v0.5/petrinet/petrinet_schema.json',
	schema_name: 'petrinet',
	description: 'Giordano2020 - SIDARTHE model of COVID-19 spread in Italy',
	model_version: '0.1',
	properties: {},
	model: {
		states: [
			{
				id: 'Susceptible',
				name: 'Susceptible',
				grounding: {
					identifiers: {
						ido: '0000514'
					},
					modifiers: {}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Diagnosed',
				name: 'Diagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						diagnosis: 'ncit:C15220'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Infected',
				name: 'Infected',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Ailing',
				name: 'Ailing',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						disease_severity: 'ncit:C25269',
						diagnosis: 'ncit:C113725'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Recognized',
				name: 'Recognized',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						diagnosis: 'ncit:C15220'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Healed',
				name: 'Healed',
				grounding: {
					identifiers: {
						ido: '0000592'
					},
					modifiers: {}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Threatened',
				name: 'Threatened',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						disease_severity: 'ncit:C25467'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'Extinct',
				name: 'Extinct',
				grounding: {
					identifiers: {
						ncit: 'C28554'
					},
					modifiers: {}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			}
		],
		transitions: [
			{
				id: 't1',
				input: ['Diagnosed', 'Susceptible'],
				output: ['Diagnosed', 'Infected'],
				properties: {
					name: 't1'
				}
			},
			{
				id: 't2',
				input: ['Ailing', 'Susceptible'],
				output: ['Ailing', 'Infected'],
				properties: {
					name: 't2'
				}
			},
			{
				id: 't3',
				input: ['Recognized', 'Susceptible'],
				output: ['Recognized', 'Infected'],
				properties: {
					name: 't3'
				}
			},
			{
				id: 't4',
				input: ['Infected', 'Susceptible'],
				output: ['Infected', 'Infected'],
				properties: {
					name: 't4'
				}
			},
			{
				id: 't5',
				input: ['Infected'],
				output: ['Diagnosed'],
				properties: {
					name: 't5'
				}
			},
			{
				id: 't6',
				input: ['Infected'],
				output: ['Ailing'],
				properties: {
					name: 't6'
				}
			},
			{
				id: 't7',
				input: ['Infected'],
				output: ['Healed'],
				properties: {
					name: 't7'
				}
			},
			{
				id: 't8',
				input: ['Diagnosed'],
				output: ['Recognized'],
				properties: {
					name: 't8'
				}
			},
			{
				id: 't9',
				input: ['Diagnosed'],
				output: ['Healed'],
				properties: {
					name: 't9'
				}
			},
			{
				id: 't10',
				input: ['Ailing'],
				output: ['Recognized'],
				properties: {
					name: 't10'
				}
			},
			{
				id: 't11',
				input: ['Ailing'],
				output: ['Healed'],
				properties: {
					name: 't11'
				}
			},
			{
				id: 't12',
				input: ['Ailing'],
				output: ['Threatened'],
				properties: {
					name: 't12'
				}
			},
			{
				id: 't13',
				input: ['Recognized'],
				output: ['Threatened'],
				properties: {
					name: 't13'
				}
			},
			{
				id: 't14',
				input: ['Recognized'],
				output: ['Healed'],
				properties: {
					name: 't14'
				}
			},
			{
				id: 't15',
				input: ['Threatened'],
				output: ['Extinct'],
				properties: {
					name: 't15'
				}
			},
			{
				id: 't16',
				input: ['Threatened'],
				output: ['Healed'],
				properties: {
					name: 't16'
				}
			}
		]
	},
	semantics: {
		ode: {
			rates: [
				{
					target: 't1',
					expression: 'Diagnosed*Susceptible*beta',
					expression_mathml:
						'<apply><times/><ci>Diagnosed</ci><ci>Susceptible</ci><ci>beta</ci></apply>'
				},
				{
					target: 't2',
					expression: 'Ailing*Susceptible*gamma',
					expression_mathml:
						'<apply><times/><ci>Ailing</ci><ci>Susceptible</ci><ci>gamma</ci></apply>'
				},
				{
					target: 't3',
					expression: 'Recognized*Susceptible*delta',
					expression_mathml:
						'<apply><times/><ci>Recognized</ci><ci>Susceptible</ci><ci>delta</ci></apply>'
				},
				{
					target: 't4',
					expression: 'Infected*Susceptible*alpha',
					expression_mathml:
						'<apply><times/><ci>Infected</ci><ci>Susceptible</ci><ci>alpha</ci></apply>'
				},
				{
					target: 't5',
					expression: 'Infected*epsilon',
					expression_mathml: '<apply><times/><ci>Infected</ci><ci>epsilon</ci></apply>'
				},
				{
					target: 't6',
					expression: 'Infected*zeta',
					expression_mathml: '<apply><times/><ci>Infected</ci><ci>zeta</ci></apply>'
				},
				{
					target: 't7',
					expression: 'Infected*lambda',
					expression_mathml: '<apply><times/><ci>Infected</ci><ci>lambda</ci></apply>'
				},
				{
					target: 't8',
					expression: 'Diagnosed*eta',
					expression_mathml: '<apply><times/><ci>Diagnosed</ci><ci>eta</ci></apply>'
				},
				{
					target: 't9',
					expression: 'Diagnosed*rho',
					expression_mathml: '<apply><times/><ci>Diagnosed</ci><ci>rho</ci></apply>'
				},
				{
					target: 't10',
					expression: 'Ailing*theta',
					expression_mathml: '<apply><times/><ci>Ailing</ci><ci>theta</ci></apply>'
				},
				{
					target: 't11',
					expression: 'Ailing*kappa',
					expression_mathml: '<apply><times/><ci>Ailing</ci><ci>kappa</ci></apply>'
				},
				{
					target: 't12',
					expression: 'Ailing*mu',
					expression_mathml: '<apply><times/><ci>Ailing</ci><ci>mu</ci></apply>'
				},
				{
					target: 't13',
					expression: 'Recognized*nu',
					expression_mathml: '<apply><times/><ci>Recognized</ci><ci>nu</ci></apply>'
				},
				{
					target: 't14',
					expression: 'Recognized*xi',
					expression_mathml: '<apply><times/><ci>Recognized</ci><ci>xi</ci></apply>'
				},
				{
					target: 't15',
					expression: 'Threatened*tau',
					expression_mathml: '<apply><times/><ci>Threatened</ci><ci>tau</ci></apply>'
				},
				{
					target: 't16',
					expression: 'Threatened*sigma',
					expression_mathml: '<apply><times/><ci>Threatened</ci><ci>sigma</ci></apply>'
				}
			],
			initials: [
				{
					target: 'Susceptible',
					expression: '0.999996300000000',
					expression_mathml: '<cn>0.99999629999999995</cn>'
				},
				{
					target: 'Diagnosed',
					expression: '3.33333333000000e-7',
					expression_mathml: '<cn>3.33333333e-7</cn>'
				},
				{
					target: 'Infected',
					expression: '3.33333333000000e-6',
					expression_mathml: '<cn>3.3333333299999999e-6</cn>'
				},
				{
					target: 'Ailing',
					expression: '1.66666666000000e-8',
					expression_mathml: '<cn>1.6666666599999999e-8</cn>'
				},
				{
					target: 'Recognized',
					expression: '3.33333333000000e-8',
					expression_mathml: '<cn>3.33333333e-8</cn>'
				},
				{
					target: 'Healed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'Threatened',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'Extinct',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				}
			],
			parameters: [
				{
					id: 'beta',
					value: 0.011,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.008799999999999999,
							maximum: 0.0132
						}
					}
				},
				{
					id: 'gamma',
					value: 0.456,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.3648,
							maximum: 0.5472
						}
					}
				},
				{
					id: 'delta',
					value: 0.011,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.008799999999999999,
							maximum: 0.0132
						}
					}
				},
				{
					id: 'alpha',
					value: 0.57,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.45599999999999996,
							maximum: 0.6839999999999999
						}
					}
				},
				{
					id: 'epsilon',
					value: 0.171,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.1368,
							maximum: 0.20520000000000002
						}
					}
				},
				{
					id: 'zeta',
					value: 0.125,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.1,
							maximum: 0.15
						}
					}
				},
				{
					id: 'lambda',
					value: 0.034,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.027200000000000002,
							maximum: 0.0408
						}
					}
				},
				{
					id: 'eta',
					value: 0.125,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.1,
							maximum: 0.15
						}
					}
				},
				{
					id: 'rho',
					value: 0.034,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.027200000000000002,
							maximum: 0.0408
						}
					}
				},
				{
					id: 'theta',
					value: 0.371,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.2968,
							maximum: 0.4452
						}
					}
				},
				{
					id: 'kappa',
					value: 0.017,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.013600000000000001,
							maximum: 0.0204
						}
					}
				},
				{
					id: 'mu',
					value: 0.017,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.013600000000000001,
							maximum: 0.0204
						}
					}
				},
				{
					id: 'nu',
					value: 0.027,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.0216,
							maximum: 0.0324
						}
					}
				},
				{
					id: 'xi',
					value: 0.017,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.013600000000000001,
							maximum: 0.0204
						}
					}
				},
				{
					id: 'tau',
					value: 0.01,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.008,
							maximum: 0.012
						}
					}
				},
				{
					id: 'sigma',
					value: 0.017,
					distribution: {
						type: 'StandardUniform1',
						parameters: {
							minimum: 0.013600000000000001,
							maximum: 0.0204
						}
					}
				}
			],
			observables: [
				{
					id: 'Cases',
					name: 'Cases',
					expression: 'Diagnosed + Recognized + Threatened',
					expression_mathml:
						'<apply><plus/><ci>Diagnosed</ci><ci>Recognized</ci><ci>Threatened</ci></apply>'
				},
				{
					id: 'Hospitalizations',
					name: 'Hospitalizations',
					expression: 'Recognized + Threatened',
					expression_mathml: '<apply><plus/><ci>Recognized</ci><ci>Threatened</ci></apply>'
				},
				{
					id: 'Deaths',
					name: 'Deaths',
					expression: 'Extinct',
					expression_mathml: '<ci>Extinct</ci>'
				}
			],
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
			license: 'CC0',
			authors: [],
			references: ['pubmed:32322102'],
			time_scale: null,
			time_start: null,
			time_end: null,
			locations: [],
			pathogens: ['ncbitaxon:2697049'],
			diseases: ['doid:0080600'],
			hosts: ['ncbitaxon:9606'],
			model_types: ['mamo:0000028']
		}
	}
};
