/* Collection of MATH/MATH-EDITOR utility functions */
import { logger } from '@/utils/logger';
import Mathml2latex from 'mathml-to-latex';
import { cloneDeep } from 'lodash';

export enum MathEditorModes {
	LIVE = 'mathLIVE',
	KATEX = 'katex'
}

// simple check to test if it's a mathml string based on opening and closing tags
export const isMathML = (mathMLString: string): boolean => {
	const regex = /<math[>\s][\s\S]*?<\/math>/s;
	return regex.test(mathMLString);
};

/**
 * Checks if a string is a mathML equation and returns its latex
 */
export const getLatexFromMathML = (mathMLString: string): string => {
	try {
		return Mathml2latex.convert(`<math display="block">${mathMLString}</math>`);
	} catch (error) {
		logger.error(error, { showToast: false, silent: true });
		return '';
	}
};

/**
 * removes redudant <mrow> tags
 */
const removeRedundantMrow = (element: Element | null): void => {
	if (element) {
		const mrowElements = Array.from(element.querySelectorAll('mrow'));
		mrowElements.forEach((mrowElement) => {
			const parentElement = mrowElement.parentElement;
			if (
				mrowElement.childElementCount === 1 &&
				parentElement &&
				!(parentElement.tagName === 'mfrac' && parentElement.children[0] === mrowElement)
			) {
				while (mrowElement.children.length > 0) {
					parentElement.insertBefore(mrowElement.children[0], mrowElement);
				}
				console.log(mrowElement);
				mrowElement.remove();
			}
		});
	}
};

/**
 * removes empty <mo> items
 */
export const removeInvisibleTimes = (element: Element | null): void => {
	if (element) {
		Array.from(element.children).forEach((child) => {
			if (child.tagName === 'mo' && child.textContent === '⁢') {
				element.removeChild(child);
			} else {
				removeInvisibleTimes(child);
			}
		});
	}
};

/**
 * removes any parentheses found in the equations
 */
export function removeParentheses(element: Element | null): void {
	if (element) {
		const moElements = Array.from(element.querySelectorAll('mo'));
		moElements.forEach((moElement) => {
			if (moElement.textContent === '(' || moElement.textContent === ')') {
				moElement.remove();
			}
		});
	}
}

/**
 * removes the "t"/time element in the equations
 */
export function removeTElement(element: Element | null): void {
	if (element) {
		const tElements = Array.from(element.querySelectorAll('mi'));

		tElements.forEach((el) => {
			if (el.textContent === 't') {
				el.remove();
			}
		});
	}
}

/**
 * Flattens the mathML structure that are made up of multiple <mrows>
 */
export function flattenMathMLElement(element: Element | null) {
	if (element) {
		const children = Array.from(element.children);

		children.forEach((child) => {
			if (child.tagName === 'mrow' && child.children.length === 1) {
				element.replaceChild(child.children[0], child);
			} else {
				flattenMathMLElement(child);
			}
		});
	}
}

// Seperates the mathMLString into seperate equations into a list
export function separateEquations(mathMLString: string): string[] {
	if (!mathMLString) return [''];

	const parser = new DOMParser();
	const mathMLDocument = parser.parseFromString(mathMLString, 'application/xml');
	mathMLDocument.querySelector('math')?.removeAttribute('xmlns');
	const mtableElement = mathMLDocument.querySelector('mtable');

	const equations: string[] = [];
	if (mtableElement) {
		const mtrElements = Array.from(mtableElement.querySelectorAll('mtr'));

		mtrElements.forEach((mtrElement) => {
			const mtdElements = Array.from(mtrElement.querySelectorAll('mtd'));
			const mrowElements = mtdElements.map((mtd) => mtd.querySelector('mrow')).filter(Boolean);

			if (mrowElements.length > 0) {
				mrowElements.forEach(removeRedundantMrow);
				mrowElements.forEach(removeInvisibleTimes);
				mrowElements.forEach(removeParentheses);
				mrowElements.forEach(flattenMathMLElement);
				mrowElements.forEach(removeTElement);

				const newMrowElement = mathMLDocument.createElement('mrow');
				mrowElements.forEach((mrow) => {
					while (mrow && mrow.children.length > 0) {
						newMrowElement.appendChild(mrow.children[0]);
					}
				});

				const newMathElement = mathMLDocument.createElement('math');
				newMathElement.appendChild(newMrowElement);
				const serializer = new XMLSerializer();
				const newRowString = serializer
					.serializeToString(newMathElement)
					.replace(/[a-zA-Z]+="[^"]*"/g, '')
					.replaceAll(' ', '')
					.replaceAll('<mrow/>', '');
				equations.push(newRowString);
			}
		});
	}
	const mi = mathMLDocument.querySelector('mi');
	if (mi) {
		equations.push(mi.innerHTML);
	}
	return equations;
}

export enum EquationSide {
	Left = 'left',
	Right = 'right'
}

// extract identifiers in a mathml equation
export const extractVariablesFromMathML = (mathML: string, side?: EquationSide): string[] => {
	const parser = new DOMParser();
	const xmlDoc = parser.parseFromString(mathML, 'text/xml');
	const variables: string[] = [];

	if (!side) {
		const miElements = xmlDoc.getElementsByTagName('mi');
		for (let i = 0; i < miElements.length; i++) {
			const miElement = miElements[i];
			const variable = miElement.textContent?.trim();
			if (variable) {
				variables.push(variable);
			}
		}
	} else {
		const moElements = xmlDoc.getElementsByTagName('mo');
		for (let i = 0; i < moElements.length; i++) {
			const moElement = moElements[i];
			const isEqualsSign = moElement.textContent?.trim() === '=';
			if (isEqualsSign) {
				let currentNode: Element | null = moElement.previousElementSibling;
				while (currentNode) {
					if (currentNode.nodeName === 'mi') {
						const variable = currentNode.textContent?.trim();
						if (variable) {
							variables.unshift(variable);
						}
					} else if (currentNode.nodeName === 'mrow') {
						if (side === EquationSide.Left) {
							break;
						}
						const innerVariables = extractVariablesFromMathML(currentNode.innerHTML, side);
						variables.unshift(...innerVariables);
						if (side === EquationSide.Right) {
							break;
						}
					}
					currentNode = currentNode.previousElementSibling;
				}
				if (side === EquationSide.Left) {
					break;
				}
			}
		}
	}

	return variables;
};

// clean a latex equation
export const cleanLatexEquations = (equations: Array<string>): Array<string> =>
	cloneDeep(equations)
		.filter((equation) => equation !== '')
		.map((equation) =>
			equation
				// Refactor to make those replaceAll one regex change
				.replaceAll('\\begin', '')
				.replaceAll('\\end', '')
				.replaceAll('\\mathrm', '')
				.replaceAll('\\right', '')
				.replaceAll('\\left', '')
				.replaceAll('{align}', '')
				.replaceAll('=&', '=')
				// scientific variables such as \beta and \gamma are not parsed when there is a '*' (with no space) placed after them - other math operaters do work though
				.replaceAll('*', ' *')
				.trim()
		);

export const mergeUnique = (a: string[], b: string[]): string[] => {
	const aUnique: string[] = [...new Set(a)];
	const bSet = new Set(b);

	aUnique.forEach((element) => {
		if (![...bSet].some((item) => item.includes(element))) {
			bSet.add(element);
		}
	});

	return Array.from(bSet);
};

export const extractUniqueParameterStrings = (a: string[], b: string[]): string[] => {
	const aUnique: string[] = [...new Set(a)];

	return aUnique.filter((element) => !b.includes(element) && !b.some((item) => item.includes(element)));
};

// given an array of numbers and a percentile number (0-100), returns the percentile value
export function percentile(values: number[], q: number): number {
	if (q < 0 || q > 100) throw new Error('Percentile must be between 0 and 100');

	values.sort((a, b) => a - b);
	const index = (q / 100) * (values.length - 1);
	const lowerIndex = Math.floor(index);
	const upperIndex = Math.ceil(index);

	if (lowerIndex === upperIndex) {
		return values[lowerIndex];
	}
	const lowerValue = values[lowerIndex];
	const upperValue = values[upperIndex];
	return lowerValue + (upperValue - lowerValue) * (index - lowerIndex);
}

export function calculateUncertaintyRange(value: number, percentage: number): { min: number; max: number } {
	const delta = value * (percentage / 100);
	const min = parseFloat((value - delta).toFixed(8));
	let max = parseFloat((value + delta).toFixed(8));

	// return a [0, 1] range if both min and max are 0
	if (min === 0 && max === 0) max = 1;

	return {
		min,
		max
	};
}

/**
 * Calculates the percentage value given a numerator and a denominator.
 *
 * @param numerator - The numerator value.
 * @param denominator - The denominator value.
 * @returns The calculated percentage value.
 */
export function calculatePercentage(numerator: number, denominator: number): number {
	if (denominator === 0) {
		return 0;
	}
	return (numerator / denominator) * 100;
}

/**
 * Sums the corresponding elements of multiple arrays of numbers.
 *
 * @param arrays - An array of arrays of numbers.
 * @returns A new array where each element is the sum of the corresponding elements of the input arrays.
 * @throws Will throw an error if the input arrays are not of the same length.
 */
export function sumArrays(...arrays: number[][]): number[] {
	if (arrays.length === 0) {
		throw new Error('At least one array is required');
	}

	const length = arrays[0].length;
	if (!arrays.every((array) => array.length === length)) {
		throw new Error('All arrays must be of the same length');
	}

	const result = new Array(length).fill(0);
	// eslint-disable-next-line
	for (const array of arrays) {
		for (let i = 0; i < length; i++) {
			result[i] += array[i];
		}
	}

	return result;
}

/**
 * Divides the corresponding elements of two arrays of numbers.
 *
 * @param array1 - The first array of numbers.
 * @param array2 - The second array of numbers.
 * @returns A new array where each element is the result of dividing the corresponding elements of `array1` by `array2`.
 * @throws Will throw an error if the input arrays are not of the same length.
 */
export function divideArrays(array1: number[], array2: number[]): number[] {
	if (array1.length !== array2.length) {
		throw new Error('Arrays must be of the same length');
	}

	const result = new Array(array1.length);
	for (let i = 0; i < array1.length; i++) {
		if (array2[i] === 0) {
			// Division by zero
			result[i] = NaN;
		}
		result[i] = array1[i] / array2[i];
	}
	return result;
}

/**
 * Calculates the percentage values given arrays of numerators and denominators.
 *
 * @param numerators - An array of numerator values.
 * @param denominators - An array of denominator values.
 * @returns An array of calculated percentage values.
 * @throws Will throw an error if the input arrays are not of the same length.
 */
export function calculatePercentages(numerators: number[], denominators: number[]): number[] {
	const ratios = divideArrays(numerators, denominators);
	return ratios.map((ratio) => (Number.isNaN(ratio) ? 0 : ratio * 100));
}
