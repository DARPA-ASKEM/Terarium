/**
 * Converts a number string from its exponential form to normal form.
 *
 * @param {string} num - The number string in exponential form to convert.
 * @returns {string} The number in normal form.
 */
export function exponentialToNumber(num: string): string {
	return parseFloat(num).toLocaleString('fullwide', { useGrouping: false });
}

/**
 * Converts a number string to its NIST form.
 *
 * @param {string} num - The number string to convert.
 * @returns {string} The number in NIST form.
 */
export function numberToNist(num: string | number) {
	num = num.toString().replace(/\s/g, '');

	if (Number.isNaN(Number(num))) return '';

	// Split the input by decimal point
	let [integerPart, decimalPart] = num.split('.');

	// Format the integer part
	if (integerPart.length > 4 || decimalPart?.length > 4) {
		integerPart = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

		if (num.includes('.') && decimalPart) {
			decimalPart = decimalPart.replace(/(\d{3})/g, '$1 ').trim();
		}
	}

	// Construct the formatted number
	let formattedNumber = integerPart;
	if (decimalPart) {
		formattedNumber += `.${decimalPart}`;
	}

	return formattedNumber;
}

/**
 * Converts a NIST number form to string form.
 *
 * @param {string} nist - The number in NIST form to convert.
 * @returns {string} The number in string form.
 */
export function nistToString(nist: string): string {
	// test if the value is formatted as NIST (contained digits and whitespace between them)
	if (/^\d+(\s\d+)*$/.test(nist)) {
		// remove all whitespace
		return nist.replace(/\s/g, '');
	}
	return nist;
}

/**
 * Converts a number string from its NIST form to normal form.
 *
 * @param {string} numStr - The number string in NIST form to convert.
 * @returns {number} The number in normal form.
 */
export function nistToNumber(numStr: string): number {
	// Remove any spaces from the formatted number
	numStr = numStr.replace(/\s/g, '');
	return parseFloat(numStr);
}

/**
 * Displays a number string in either exponential form or NIST form, depending on its length.
 *
 * @param {string} num - The number string to display.
 * @returns {string} The number in either exponential form or NIST form.
 */
export function displayNumber(num: string | number): string {
	num = num.toString().replace(/\s/g, '');
	const number = fixPrecisionError(parseFloat(num));
	if (countDigits(number) > 6) return number.toExponential(3);
	return numberToNist(number.toString());
}

/**
 * Counts the number of digits in a given number.
 */
export function countDigits(num: number): number {
	if (Number.isNaN(num)) return 0;
	let digitString = num.toString();
	// Remove negative sign if present
	if (digitString.startsWith('-')) {
		digitString = digitString.substring(1);
	}
	// Remove decimal point if present
	digitString = digitString.replace('.', '');
	return digitString.length;
}

/**
 * Fixes floating-point precision errors by rounding the number to a specified precision.
 * JavaScript's floating-point arithmetic can introduce small errors (e.g., 0.1 + 0.2 = 0.30000000000000004).
 * This causes issues like 0.30000000000000004 ends up as a long number with insignificant trailing digits or '3.000e-1' (with exponential formatting) instead of '0.3'.
 * This function rounds the number to mitigate such errors (e.g., fixFloatingPrecisionError(0.1 + 0.2) = 0.3).
 * For num > 0, the function rounds to 3 decimal places.
 *
 * !! Note: Only use this function for display or formatting purposes. Do not use it for calculations. !!
 */
export function fixPrecisionError(num: number): number {
	return num < 1 && num > -1 ? Number(num.toFixed(15)) : Number(num.toFixed(3));
}

/**
 * Scrubs the input string by removing all whitespace and checks if it can be parsed as a number.
 *
 * @param {string} v - The input string to scrub and parse.
 * @returns {boolean} - Returns true if the scrubbed string can be parsed as a number (either in decimal or exponential form), otherwise returns false.
 *
 * The function performs the following checks:
 * 1. If the last character is a decimal point, it returns false.
 * 2. If the scrubbed string can be parsed as a number, it returns true.
 * 3. If the scrubbed string is in exponential form (i.e., contains exactly one 'e' or 'E'), it checks if the part before the 'e' or 'E' can be parsed as a number and if the part after the 'e' or 'E' is an integer. If both conditions are met, it returns true.
 */
export function scrubAndParse(v) {
	// remove all whitespace
	const scrubbed = v.replace(/\s/g, '');

	// check if last digit is a decimal point
	if (scrubbed[scrubbed.length - 1] === '.') return false;

	// check if the string is a number
	if (!Number.isNaN(Number(scrubbed))) return true;

	// check if the string is in exponential form and if there's only one 'e'/'E'
	const parts = scrubbed.split(/[eE]/);
	if (parts.length > 2) return false;

	// check if the string is in exponential form and if the second part is an integer
	if (parts.length === 2) {
		if (Number(parts[0])) {
			if (Number.isInteger(parts[1])) return true;
		}
	} else if (Number(parts[0])) return true;
	return false;
}

export function toScientificNotation(num: number) {
	if (num === 0) return { A: 0, B: 0 };
	const exponent = Math.floor(Math.log10(Math.abs(num)));
	const mantissa = num / 10 ** exponent;

	return { mantissa, exponent };
}

export function roundNumber(number: number): number {
	return Math.round(number * 1000) / 1000;
}
