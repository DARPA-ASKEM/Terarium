import { Grounding } from '@/types/Types';

// These are common properties among states, parameters, transitions, observables, etc
// Useful for UI components that need to display these properties
export type ModelPartItem = {
	id: string;
	name?: string;
	description?: string;
	grounding?: Grounding;
	unitExpression?: string;
	expression?: string;
	expression_mathml?: string;
	// Transition/rate
	templateId?: string;
	input?: string;
	output?: string;
};

export enum StratifiedMatrix {
	Initials = 'initials',
	Parameters = 'parameters',
	Rates = 'rates'
}
