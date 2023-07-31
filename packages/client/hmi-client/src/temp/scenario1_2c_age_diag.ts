export const mira_model = {
	id: '9ui',
	name: 'Scenario 1 age/diagnosis stratified',
	schema:
		'https://raw.githubusercontent.com/DARPA-ASKEM/Model-Representations/petrinet_v0.5/petrinet/petrinet_schema.json',
	schema_name: 'petrinet',
	description: 'Scenario 1 age/diagnosis stratified',
	model_version: '0.1',
	properties: {},
	model: {
		states: [
			{
				id: 'S_middle_aged',
				name: 'S_middle_aged',
				grounding: {
					identifiers: {
						ido: '0000514'
					},
					modifiers: {
						age: 'middle_aged'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'I_middle_aged_diagnosed',
				name: 'I_middle_aged_diagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						age: 'middle_aged',
						diagnosis: 'diagnosed'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'E_middle_aged',
				name: 'E_middle_aged',
				grounding: {
					identifiers: {
						apollosv: '0000154'
					},
					modifiers: {
						age: 'middle_aged'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'I_middle_aged_undiagnosed',
				name: 'I_middle_aged_undiagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						age: 'middle_aged',
						diagnosis: 'undiagnosed'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'I_old_diagnosed',
				name: 'I_old_diagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						age: 'old',
						diagnosis: 'diagnosed'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'I_old_undiagnosed',
				name: 'I_old_undiagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						age: 'old',
						diagnosis: 'undiagnosed'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'I_young_diagnosed',
				name: 'I_young_diagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						age: 'young',
						diagnosis: 'diagnosed'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'I_young_undiagnosed',
				name: 'I_young_undiagnosed',
				grounding: {
					identifiers: {
						ido: '0000511'
					},
					modifiers: {
						age: 'young',
						diagnosis: 'undiagnosed'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'S_old',
				name: 'S_old',
				grounding: {
					identifiers: {
						ido: '0000514'
					},
					modifiers: {
						age: 'old'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'E_old',
				name: 'E_old',
				grounding: {
					identifiers: {
						apollosv: '0000154'
					},
					modifiers: {
						age: 'old'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'S_young',
				name: 'S_young',
				grounding: {
					identifiers: {
						ido: '0000514'
					},
					modifiers: {
						age: 'young'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'E_young',
				name: 'E_young',
				grounding: {
					identifiers: {
						apollosv: '0000154'
					},
					modifiers: {
						age: 'young'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'R_middle_aged',
				name: 'R_middle_aged',
				grounding: {
					identifiers: {
						ido: '0000592'
					},
					modifiers: {
						age: 'middle_aged'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'R_old',
				name: 'R_old',
				grounding: {
					identifiers: {
						ido: '0000592'
					},
					modifiers: {
						age: 'old'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'R_young',
				name: 'R_young',
				grounding: {
					identifiers: {
						ido: '0000592'
					},
					modifiers: {
						age: 'young'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'D_middle_aged',
				name: 'D_middle_aged',
				grounding: {
					identifiers: {
						ncit: 'C28554'
					},
					modifiers: {
						age: 'middle_aged'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'D_old',
				name: 'D_old',
				grounding: {
					identifiers: {
						ncit: 'C28554'
					},
					modifiers: {
						age: 'old'
					}
				},
				units: {
					expression: '1',
					expression_mathml: '<cn>1</cn>'
				}
			},
			{
				id: 'D_young',
				name: 'D_young',
				grounding: {
					identifiers: {
						ncit: 'C28554'
					},
					modifiers: {
						age: 'young'
					}
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
				input: ['I_middle_aged_diagnosed', 'S_middle_aged'],
				output: ['I_middle_aged_diagnosed', 'E_middle_aged'],
				properties: {
					name: 't1'
				}
			},
			{
				id: 't2',
				input: ['I_middle_aged_undiagnosed', 'S_middle_aged'],
				output: ['I_middle_aged_undiagnosed', 'E_middle_aged'],
				properties: {
					name: 't2'
				}
			},
			{
				id: 't3',
				input: ['I_old_diagnosed', 'S_middle_aged'],
				output: ['I_old_diagnosed', 'E_middle_aged'],
				properties: {
					name: 't3'
				}
			},
			{
				id: 't4',
				input: ['I_old_undiagnosed', 'S_middle_aged'],
				output: ['I_old_undiagnosed', 'E_middle_aged'],
				properties: {
					name: 't4'
				}
			},
			{
				id: 't5',
				input: ['I_young_diagnosed', 'S_middle_aged'],
				output: ['I_young_diagnosed', 'E_middle_aged'],
				properties: {
					name: 't5'
				}
			},
			{
				id: 't6',
				input: ['I_young_undiagnosed', 'S_middle_aged'],
				output: ['I_young_undiagnosed', 'E_middle_aged'],
				properties: {
					name: 't6'
				}
			},
			{
				id: 't7',
				input: ['I_old_diagnosed', 'S_old'],
				output: ['I_old_diagnosed', 'E_old'],
				properties: {
					name: 't7'
				}
			},
			{
				id: 't8',
				input: ['I_old_undiagnosed', 'S_old'],
				output: ['I_old_undiagnosed', 'E_old'],
				properties: {
					name: 't8'
				}
			},
			{
				id: 't9',
				input: ['I_middle_aged_diagnosed', 'S_old'],
				output: ['I_middle_aged_diagnosed', 'E_old'],
				properties: {
					name: 't9'
				}
			},
			{
				id: 't10',
				input: ['I_middle_aged_undiagnosed', 'S_old'],
				output: ['I_middle_aged_undiagnosed', 'E_old'],
				properties: {
					name: 't10'
				}
			},
			{
				id: 't11',
				input: ['I_young_diagnosed', 'S_old'],
				output: ['I_young_diagnosed', 'E_old'],
				properties: {
					name: 't11'
				}
			},
			{
				id: 't12',
				input: ['I_young_undiagnosed', 'S_old'],
				output: ['I_young_undiagnosed', 'E_old'],
				properties: {
					name: 't12'
				}
			},
			{
				id: 't13',
				input: ['I_young_diagnosed', 'S_young'],
				output: ['I_young_diagnosed', 'E_young'],
				properties: {
					name: 't13'
				}
			},
			{
				id: 't14',
				input: ['I_young_undiagnosed', 'S_young'],
				output: ['I_young_undiagnosed', 'E_young'],
				properties: {
					name: 't14'
				}
			},
			{
				id: 't15',
				input: ['I_middle_aged_diagnosed', 'S_young'],
				output: ['I_middle_aged_diagnosed', 'E_young'],
				properties: {
					name: 't15'
				}
			},
			{
				id: 't16',
				input: ['I_middle_aged_undiagnosed', 'S_young'],
				output: ['I_middle_aged_undiagnosed', 'E_young'],
				properties: {
					name: 't16'
				}
			},
			{
				id: 't17',
				input: ['I_old_diagnosed', 'S_young'],
				output: ['I_old_diagnosed', 'E_young'],
				properties: {
					name: 't17'
				}
			},
			{
				id: 't18',
				input: ['I_old_undiagnosed', 'S_young'],
				output: ['I_old_undiagnosed', 'E_young'],
				properties: {
					name: 't18'
				}
			},
			{
				id: 't19',
				input: ['E_middle_aged'],
				output: ['I_middle_aged_diagnosed'],
				properties: {
					name: 't19'
				}
			},
			{
				id: 't20',
				input: ['E_middle_aged'],
				output: ['I_middle_aged_undiagnosed'],
				properties: {
					name: 't20'
				}
			},
			{
				id: 't21',
				input: ['E_old'],
				output: ['I_old_diagnosed'],
				properties: {
					name: 't21'
				}
			},
			{
				id: 't22',
				input: ['E_old'],
				output: ['I_old_undiagnosed'],
				properties: {
					name: 't22'
				}
			},
			{
				id: 't23',
				input: ['E_young'],
				output: ['I_young_diagnosed'],
				properties: {
					name: 't23'
				}
			},
			{
				id: 't24',
				input: ['E_young'],
				output: ['I_young_undiagnosed'],
				properties: {
					name: 't24'
				}
			},
			{
				id: 't25',
				input: ['I_middle_aged_diagnosed'],
				output: ['R_middle_aged'],
				properties: {
					name: 't25'
				}
			},
			{
				id: 't26',
				input: ['I_middle_aged_undiagnosed'],
				output: ['R_middle_aged'],
				properties: {
					name: 't26'
				}
			},
			{
				id: 't27',
				input: ['I_old_diagnosed'],
				output: ['R_old'],
				properties: {
					name: 't27'
				}
			},
			{
				id: 't28',
				input: ['I_old_undiagnosed'],
				output: ['R_old'],
				properties: {
					name: 't28'
				}
			},
			{
				id: 't29',
				input: ['I_young_diagnosed'],
				output: ['R_young'],
				properties: {
					name: 't29'
				}
			},
			{
				id: 't30',
				input: ['I_young_undiagnosed'],
				output: ['R_young'],
				properties: {
					name: 't30'
				}
			},
			{
				id: 't31',
				input: ['I_middle_aged_diagnosed'],
				output: ['D_middle_aged'],
				properties: {
					name: 't31'
				}
			},
			{
				id: 't32',
				input: ['I_middle_aged_undiagnosed'],
				output: ['D_middle_aged'],
				properties: {
					name: 't32'
				}
			},
			{
				id: 't33',
				input: ['I_old_diagnosed'],
				output: ['D_old'],
				properties: {
					name: 't33'
				}
			},
			{
				id: 't34',
				input: ['I_old_undiagnosed'],
				output: ['D_old'],
				properties: {
					name: 't34'
				}
			},
			{
				id: 't35',
				input: ['I_young_diagnosed'],
				output: ['D_young'],
				properties: {
					name: 't35'
				}
			},
			{
				id: 't36',
				input: ['I_young_undiagnosed'],
				output: ['D_young'],
				properties: {
					name: 't36'
				}
			},
			{
				id: 't37',
				input: ['R_middle_aged'],
				output: ['S_middle_aged'],
				properties: {
					name: 't37'
				}
			},
			{
				id: 't38',
				input: ['R_old'],
				output: ['S_old'],
				properties: {
					name: 't38'
				}
			},
			{
				id: 't39',
				input: ['R_young'],
				output: ['S_young'],
				properties: {
					name: 't39'
				}
			},
			{
				id: 't40',
				input: ['I_middle_aged_undiagnosed'],
				output: ['I_middle_aged_diagnosed'],
				properties: {
					name: 't40'
				}
			},
			{
				id: 't41',
				input: ['I_old_undiagnosed'],
				output: ['I_old_diagnosed'],
				properties: {
					name: 't41'
				}
			},
			{
				id: 't42',
				input: ['I_young_undiagnosed'],
				output: ['I_young_diagnosed'],
				properties: {
					name: 't42'
				}
			}
		]
	},
	semantics: {
		ode: {
			rates: [
				{
					target: 't1',
					expression:
						'I_middle_aged_diagnosed*S_middle_aged*kappa_0_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_middle_aged_diagnosed</ci><ci>S_middle_aged</ci><ci>kappa_0_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't2',
					expression:
						'I_middle_aged_undiagnosed*S_middle_aged*kappa_0_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_middle_aged_undiagnosed</ci><ci>S_middle_aged</ci><ci>kappa_0_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't3',
					expression:
						'I_old_diagnosed*S_middle_aged*kappa_1_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_old_diagnosed</ci><ci>S_middle_aged</ci><ci>kappa_1_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't4',
					expression:
						'I_old_undiagnosed*S_middle_aged*kappa_1_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_old_undiagnosed</ci><ci>S_middle_aged</ci><ci>kappa_1_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't5',
					expression:
						'I_young_diagnosed*S_middle_aged*kappa_2_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_young_diagnosed</ci><ci>S_middle_aged</ci><ci>kappa_2_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't6',
					expression:
						'I_young_undiagnosed*S_middle_aged*kappa_2_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_young_undiagnosed</ci><ci>S_middle_aged</ci><ci>kappa_2_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't7',
					expression:
						'I_old_diagnosed*S_old*kappa_3_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_old_diagnosed</ci><ci>S_old</ci><ci>kappa_3_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't8',
					expression:
						'I_old_undiagnosed*S_old*kappa_3_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_old_undiagnosed</ci><ci>S_old</ci><ci>kappa_3_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't9',
					expression:
						'I_middle_aged_diagnosed*S_old*kappa_4_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_middle_aged_diagnosed</ci><ci>S_old</ci><ci>kappa_4_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't10',
					expression:
						'I_middle_aged_undiagnosed*S_old*kappa_4_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_middle_aged_undiagnosed</ci><ci>S_old</ci><ci>kappa_4_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't11',
					expression:
						'I_young_diagnosed*S_old*kappa_5_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_young_diagnosed</ci><ci>S_old</ci><ci>kappa_5_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't12',
					expression:
						'I_young_undiagnosed*S_old*kappa_5_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_young_undiagnosed</ci><ci>S_old</ci><ci>kappa_5_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't13',
					expression:
						'I_young_diagnosed*S_young*kappa_6_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_young_diagnosed</ci><ci>S_young</ci><ci>kappa_6_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't14',
					expression:
						'I_young_undiagnosed*S_young*kappa_6_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_young_undiagnosed</ci><ci>S_young</ci><ci>kappa_6_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't15',
					expression:
						'I_middle_aged_diagnosed*S_young*kappa_7_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_middle_aged_diagnosed</ci><ci>S_young</ci><ci>kappa_7_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't16',
					expression:
						'I_middle_aged_undiagnosed*S_young*kappa_7_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_middle_aged_undiagnosed</ci><ci>S_young</ci><ci>kappa_7_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't17',
					expression:
						'I_old_diagnosed*S_young*kappa_8_0*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_old_diagnosed</ci><ci>S_young</ci><ci>kappa_8_0</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't18',
					expression:
						'I_old_undiagnosed*S_young*kappa_8_1*(beta_nc + (beta_c - beta_nc)/(1 + exp(-k_2*(-t + t_1))) + (-beta_c + beta_s)/(1 + exp(-k_1*(-t + t_0))))/N',
					expression_mathml:
						'<apply><divide/><apply><times/><ci>I_old_undiagnosed</ci><ci>S_young</ci><ci>kappa_8_1</ci><apply><plus/><ci>beta_nc</ci><apply><divide/><apply><minus/><ci>beta_c</ci><ci>beta_nc</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_2</ci><apply><minus/><ci>t_1</ci><ci>t</ci></apply></apply></apply></apply></apply></apply><apply><divide/><apply><plus/><apply><minus/><ci>beta_c</ci></apply><ci>beta_s</ci></apply><apply><plus/><cn>1</cn><apply><exp/><apply><minus/><apply><times/><ci>k_1</ci><apply><minus/><ci>t_0</ci><ci>t</ci></apply></apply></apply></apply></apply></apply></apply></apply><ci>N</ci></apply>'
				},
				{
					target: 't19',
					expression: 'E_middle_aged*delta',
					expression_mathml: '<apply><times/><ci>E_middle_aged</ci><ci>delta</ci></apply>'
				},
				{
					target: 't20',
					expression: 'E_middle_aged*delta',
					expression_mathml: '<apply><times/><ci>E_middle_aged</ci><ci>delta</ci></apply>'
				},
				{
					target: 't21',
					expression: 'E_old*delta',
					expression_mathml: '<apply><times/><ci>E_old</ci><ci>delta</ci></apply>'
				},
				{
					target: 't22',
					expression: 'E_old*delta',
					expression_mathml: '<apply><times/><ci>E_old</ci><ci>delta</ci></apply>'
				},
				{
					target: 't23',
					expression: 'E_young*delta',
					expression_mathml: '<apply><times/><ci>E_young</ci><ci>delta</ci></apply>'
				},
				{
					target: 't24',
					expression: 'E_young*delta',
					expression_mathml: '<apply><times/><ci>E_young</ci><ci>delta</ci></apply>'
				},
				{
					target: 't25',
					expression: 'I_middle_aged_diagnosed*gamma*(1 - alpha)',
					expression_mathml:
						'<apply><times/><ci>I_middle_aged_diagnosed</ci><ci>gamma</ci><apply><minus/><cn>1</cn><ci>alpha</ci></apply></apply>'
				},
				{
					target: 't26',
					expression: 'I_middle_aged_undiagnosed*gamma*(1 - alpha)',
					expression_mathml:
						'<apply><times/><ci>I_middle_aged_undiagnosed</ci><ci>gamma</ci><apply><minus/><cn>1</cn><ci>alpha</ci></apply></apply>'
				},
				{
					target: 't27',
					expression: 'I_old_diagnosed*gamma*(1 - alpha)',
					expression_mathml:
						'<apply><times/><ci>I_old_diagnosed</ci><ci>gamma</ci><apply><minus/><cn>1</cn><ci>alpha</ci></apply></apply>'
				},
				{
					target: 't28',
					expression: 'I_old_undiagnosed*gamma*(1 - alpha)',
					expression_mathml:
						'<apply><times/><ci>I_old_undiagnosed</ci><ci>gamma</ci><apply><minus/><cn>1</cn><ci>alpha</ci></apply></apply>'
				},
				{
					target: 't29',
					expression: 'I_young_diagnosed*gamma*(1 - alpha)',
					expression_mathml:
						'<apply><times/><ci>I_young_diagnosed</ci><ci>gamma</ci><apply><minus/><cn>1</cn><ci>alpha</ci></apply></apply>'
				},
				{
					target: 't30',
					expression: 'I_young_undiagnosed*gamma*(1 - alpha)',
					expression_mathml:
						'<apply><times/><ci>I_young_undiagnosed</ci><ci>gamma</ci><apply><minus/><cn>1</cn><ci>alpha</ci></apply></apply>'
				},
				{
					target: 't31',
					expression: 'I_middle_aged_diagnosed*alpha*rho',
					expression_mathml:
						'<apply><times/><ci>I_middle_aged_diagnosed</ci><ci>alpha</ci><ci>rho</ci></apply>'
				},
				{
					target: 't32',
					expression: 'I_middle_aged_undiagnosed*alpha*rho',
					expression_mathml:
						'<apply><times/><ci>I_middle_aged_undiagnosed</ci><ci>alpha</ci><ci>rho</ci></apply>'
				},
				{
					target: 't33',
					expression: 'I_old_diagnosed*alpha*rho',
					expression_mathml:
						'<apply><times/><ci>I_old_diagnosed</ci><ci>alpha</ci><ci>rho</ci></apply>'
				},
				{
					target: 't34',
					expression: 'I_old_undiagnosed*alpha*rho',
					expression_mathml:
						'<apply><times/><ci>I_old_undiagnosed</ci><ci>alpha</ci><ci>rho</ci></apply>'
				},
				{
					target: 't35',
					expression: 'I_young_diagnosed*alpha*rho',
					expression_mathml:
						'<apply><times/><ci>I_young_diagnosed</ci><ci>alpha</ci><ci>rho</ci></apply>'
				},
				{
					target: 't36',
					expression: 'I_young_undiagnosed*alpha*rho',
					expression_mathml:
						'<apply><times/><ci>I_young_undiagnosed</ci><ci>alpha</ci><ci>rho</ci></apply>'
				},
				{
					target: 't37',
					expression: 'R_middle_aged*epsilon',
					expression_mathml: '<apply><times/><ci>R_middle_aged</ci><ci>epsilon</ci></apply>'
				},
				{
					target: 't38',
					expression: 'R_old*epsilon',
					expression_mathml: '<apply><times/><ci>R_old</ci><ci>epsilon</ci></apply>'
				},
				{
					target: 't39',
					expression: 'R_young*epsilon',
					expression_mathml: '<apply><times/><ci>R_young</ci><ci>epsilon</ci></apply>'
				},
				{
					target: 't40',
					expression: 'I_middle_aged_undiagnosed*p_undiagnosed_diagnosed',
					expression_mathml:
						'<apply><times/><ci>I_middle_aged_undiagnosed</ci><ci>p_undiagnosed_diagnosed</ci></apply>'
				},
				{
					target: 't41',
					expression: 'I_old_undiagnosed*p_undiagnosed_diagnosed',
					expression_mathml:
						'<apply><times/><ci>I_old_undiagnosed</ci><ci>p_undiagnosed_diagnosed</ci></apply>'
				},
				{
					target: 't42',
					expression: 'I_young_undiagnosed*p_undiagnosed_diagnosed',
					expression_mathml:
						'<apply><times/><ci>I_young_undiagnosed</ci><ci>p_undiagnosed_diagnosed</ci></apply>'
				}
			],
			initials: [
				{
					target: 'S_middle_aged',
					expression: '0.3333332738095238',
					expression_mathml: '<cn>0.3333332738095238015</cn>'
				},
				{
					target: 'I_middle_aged_diagnosed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'E_middle_aged',
					expression: '5.952380952380953e-8',
					expression_mathml: '<cn>5.952380952380953002e-8</cn>'
				},
				{
					target: 'I_middle_aged_undiagnosed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'I_old_diagnosed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'I_old_undiagnosed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'I_young_diagnosed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'I_young_undiagnosed',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'S_old',
					expression: '0.3333332738095238',
					expression_mathml: '<cn>0.3333332738095238015</cn>'
				},
				{
					target: 'E_old',
					expression: '5.952380952380953e-8',
					expression_mathml: '<cn>5.952380952380953002e-8</cn>'
				},
				{
					target: 'S_young',
					expression: '0.3333332738095238',
					expression_mathml: '<cn>0.3333332738095238015</cn>'
				},
				{
					target: 'E_young',
					expression: '5.952380952380953e-8',
					expression_mathml: '<cn>5.952380952380953002e-8</cn>'
				},
				{
					target: 'R_middle_aged',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'R_old',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'R_young',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'D_middle_aged',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'D_old',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				},
				{
					target: 'D_young',
					expression: '0.0',
					expression_mathml: '<cn>0.0</cn>'
				}
			],
			parameters: [
				{
					id: 'N',
					value: 1.0,
					units: {
						expression: '1',
						expression_mathml: '<cn>1</cn>'
					}
				},
				{
					id: 'beta_c',
					value: 0.4,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'beta_nc',
					value: 0.5,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'beta_s',
					value: 1.0,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'k_1',
					value: 5.0,
					units: {
						expression: '1',
						expression_mathml: '<cn>1</cn>'
					}
				},
				{
					id: 'k_2',
					value: 1.0,
					units: {
						expression: '1',
						expression_mathml: '<cn>1</cn>'
					}
				},
				{
					id: 'kappa_0_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 't_0',
					value: 89.0,
					units: {
						expression: 'day',
						expression_mathml: '<ci>day</ci>'
					}
				},
				{
					id: 't_1',
					value: 154.0,
					units: {
						expression: 'day',
						expression_mathml: '<ci>day</ci>'
					}
				},
				{
					id: 'kappa_0_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_1_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_1_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_2_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_2_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_3_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_3_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_4_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_4_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_5_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_5_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_6_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_6_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_7_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_7_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_8_0',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'kappa_8_1',
					value: 0.45454545454545453,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'delta',
					value: 0.2,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'alpha',
					value: 6.4e-5,
					units: {
						expression: '1',
						expression_mathml: '<cn>1</cn>'
					}
				},
				{
					id: 'gamma',
					value: 0.09090909090909091,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'rho',
					value: 0.1111111111111111,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'epsilon',
					value: 0.011111111111111112,
					units: {
						expression: '1/day',
						expression_mathml: '<apply><power/><ci>day</ci><cn>-1</cn></apply>'
					}
				},
				{
					id: 'p_undiagnosed_diagnosed',
					value: 0.1
				}
			],
			observables: [
				{
					id: 'infected',
					name: 'infected',
					expression:
						'I_middle_aged_diagnosed + I_middle_aged_undiagnosed + I_old_diagnosed + I_old_undiagnosed + I_young_diagnosed + I_young_undiagnosed',
					expression_mathml:
						'<apply><plus/><ci>I_middle_aged_diagnosed</ci><ci>I_middle_aged_undiagnosed</ci><ci>I_old_diagnosed</ci><ci>I_old_undiagnosed</ci><ci>I_young_diagnosed</ci><ci>I_young_undiagnosed</ci></apply>'
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
			authors: [],
			references: [],
			locations: [],
			pathogens: [],
			diseases: [],
			hosts: [],
			model_types: []
		}
	}
};
