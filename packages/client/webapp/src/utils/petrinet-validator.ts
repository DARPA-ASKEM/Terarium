export interface Petrinet {
	S: State[]; // List of state names
	T: Transition[]; // List of transition names
	I: Input[]; // List of inputs
	O: Output[]; // List of outputs
}

interface State {
	sname: string;
}
interface Transition {
	tname: string;
}
interface Input {
	// Identifies the states connected by an edge going from state -> transition
	it: number; // Transition ID which is the target
	is: number; // State ID which is the source
}
interface Output {
	// Identifies the states connected by an edge going from transition -> state
	ot: number; // Transition ID which is the source
	os: number; // State ID which is the target
}

/*
	Validates petrinet - check #2 must come before check #5 to avoid an infinite loop
*/
export const petrinetValidator = (
	petrinet: Petrinet,
	isBoundedPetrinet: boolean = true
): boolean => {
	const { S, T, I, O } = petrinet;
	// console.log(petrinet);

	/* ----- 1. Requires at least one edge ----- */
	if (I.length < 1 && O.length < 1) {
		console.log('#1');
		return false;
	}

	const checkIfSourceOrTarget = (linkedIDs: number[], lastNodeID: number): boolean => {
		for (let id = 1; id < lastNodeID; id++) {
			if (!linkedIDs.includes(id)) {
				console.log('#2');
				return false;
			}
		}
		return true;
	};

	/* ----- 2a. Check if every transition node is bounded by state nodes (a source AND a target) ----- */
	if (isBoundedPetrinet) {
		const sourceTransitionIDs: number[] = [...new Set(O.map((output) => output.ot))];
		const targetTransitionIDs: number[] = [...new Set(I.map((input) => input.it))];
		if (
			!checkIfSourceOrTarget(sourceTransitionIDs, T.length + 1) ||
			!checkIfSourceOrTarget(targetTransitionIDs, T.length + 1)
		)
			return false;
	} else {
		/* ----- 2b. If petrinet is unbounded check that every transition node is at least either a source OR a target ----- */
		const linkedTransitionIDs: number[] = [
			...new Set([...I.map((input) => input.it), ...O.map((output) => output.ot)])
		];
		if (!checkIfSourceOrTarget(linkedTransitionIDs, T.length + 1)) return false;
	}

	/* ----- 3. Check that every state node is at least either a source OR a target ----- */
	const linkedStateIDs: number[] = [
		...new Set([...I.map((input) => input.is), ...O.map((output) => output.os)])
	];
	if (!checkIfSourceOrTarget(linkedStateIDs, S.length + 1)) return false;

	/* ----- 4. Make sure there aren't multiple petrinet bodies ----- */
	const statesSurroundingTransitions: number[][] = [];
	for (let id = 1; id < T.length + 1; id++) {
		// Save all the states where the current transition is a source or a target
		statesSurroundingTransitions.push([
			...I.filter((input) => input.it === id).map((input) => input.is),
			...O.filter((output) => output.ot === id).map((output) => output.os)
		]);
	}
	// console.log(statesSurroundingTransitions)

	const connectedStates: number[] = statesSurroundingTransitions[0];
	let potentialConnections: number[][] = [];
	// Merge all the arrays in statesSurroundingTransitions that have common values
	do {
		const statesToMerge: number[][] =
			potentialConnections.length > 0 ? potentialConnections : statesSurroundingTransitions;
		potentialConnections = [];

		for (let i = 0; i < statesToMerge.length; i++) {
			if (connectedStates.some((anyPlace) => statesToMerge[i].includes(anyPlace))) {
				connectedStates.push(...statesToMerge[i]);
			} else {
				potentialConnections.push(statesToMerge[i]);
			}
		}
		// console.log([...new Set(connectedStates)]);
		// console.log(potentialConnections);

		// If the potential connections from the last iteration are the exact same then there is more than one petrinet body
		if (statesToMerge.length === potentialConnections.length) {
			console.log('#5');
			return false;
		}
	} while (potentialConnections.length > 0);

	return true; // All checks have been successfully passed
};
