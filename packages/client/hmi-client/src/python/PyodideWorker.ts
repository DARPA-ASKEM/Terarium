import { loadPyodide } from 'pyodide';
import { PyProxy } from 'pyodide/ffi';

const variableMap: Object = {
	S: 1,
	I: 2,
	R: 'x + y',
	x: 100,
	y: 1000,
	N: 'S + I + R'
};

const pyodide = await loadPyodide({
	indexURL: 'https://cdn.jsdelivr.net/pyodide/v0.25.1/full'
});
await pyodide.loadPackage(['sympy', 'numpy', 'pandas']);
pyodide.runPython('import sympy');
pyodide.runPython('import numpy as np');
pyodide.runPython('import pandas as pd');
pyodide.runPython(
	'from sympy.parsing.sympy_parser import parse_expr, standard_transformations, implicit_multiplication_application, convert_xor'
);
pyodide.runPython('from sympy.printing.latex import latex');
pyodide.runPython('from sympy.abc import _clash1, _clash2, _clash');
pyodide.runPython('from sympy import S');

// Utility function to resolve nested subsitutions - not used
pyodide.runPython(`
def recursive_sub(expr, replace):
				for _ in range(0, len(replace) + 1):
								new_expr = expr.subs(replace)
								if new_expr == expr:
												return new_expr, True
								else:
												expr = new_expr
				return new_expr, False
`);

pyodide.runPython(`
def serialize_expr(expr):
        eq = sympy.S(expr, locals=_clash)
        return {
                "latex": latex(eq),
                "mathml": sympy.mathml(eq),
                "str": str(eq)
        }
`);

/**
 * Calculates sensitivity ranking scores for multiple outcomes based on their relationships with input parameters.
 *
 * Algorithm:
 * 1. Converts input data to pandas DataFrame
 * 2. For each outcome of interest (ooi):
 *    - Formats parameter names with 'persistent_' prefix
 *    - Normalizes parameter and outcome values to [0,1] range
 *    - Calculates linear regression coefficient between each parameter and outcome
 *    - Stores coefficient as sensitivity score
 *
 * @param {Array<Object>} data - Raw data containing parameters and outcomes
 * @param {Array<string>} outcomesofInterest - Names of outcome variables to analyze
 * @param {Array<string>} parametersOfInterest - Names of input parameters to evaluate
 * @returns {Object} Dictionary with structure {scores_by_ooi: {outcome: {parameter: score}}}
 *                   where score is the linear regression coefficient indicating sensitivity
 *
 * For example: output = get_ranking_scores(data, ['deaths', 'cases'], ['R0', 'gamma'])
 * output = {
 *  scores_by_ooi: {
 * 	deaths: {
 * 		R0: 0.5,
 * 		gamma: 0.3
 * 	},
 * 	cases: {
 * 		R0: 0.2,
 * 		gamma: 0.1
 * 	}
 */
pyodide.runPython(`
	def get_ranking_scores(data, outcomesofInterest, parametersOfInterest):
		d1 = pd.DataFrame(data)
		oois = outcomesofInterest # outcome of interest
		pois = parametersOfInterest # parameters of interest

		scores_by_ooi = {}
		for ooi in oois:
			# Column names for parameters
			pois_ = [f'persistent_{p}_param' for p in pois]

			poi_scores = {}
			for p, original_p in zip(pois_, pois):
					x, y = d1[[p, ooi]].sort_values(by = p).values.transpose()
					x = (x - y.min()) / ((x.max() - x.min() or 1))
					y = (y - y.min()) / ((y.max() - y.min() or 1))
					coef = np.polyfit(x, y, 1)
					poi_scores[original_p] = coef[0]

			scores_by_ooi[ooi] = poi_scores

		return { "scores_by_ooi": scores_by_ooi }
`);

// Bootstrap
const keys = Object.keys(variableMap);
pyodide.runPython(`
    ${keys.join(',')} = sympy.symbols('${keys.join(' ')}')
`);

postMessage(true);

// There are certain symbols or tokens that cause sympy parse to fallover - eg lambda
// These provides encoding/encoding functions to get around these problems.
const encodeParseExpr = (v: string) => {
	let expr = v.toString().replaceAll('lambda', 'XXlambdaXX');
	expr = expr.replaceAll('Ci', 'XXCiXX');
	expr = expr.replaceAll('S', 'XXSXX');
	return expr;
};
const revertParseExpr = (v: string) => {
	let resultStr = v.replaceAll('XXlambdaXX', 'lambda');
	resultStr = resultStr.replaceAll('XXSXX', 'S');
	resultStr = resultStr.replaceAll('XXCiXX', 'Ci');
	return resultStr;
};

const evaluateExpression = (expressionStr: string, symbolsTable: Object) => {
	// if number-like, return as is
	if (!Number.isNaN(parseFloat(expressionStr))) {
		return expressionStr;
	}
	const subs: any[] = [];
	Object.keys(symbolsTable).forEach((key) => {
		subs.push(`${encodeParseExpr(key)}: ${symbolsTable[key]}`);
	});

	const skeys = Object.keys(symbolsTable);
	pyodide.runPython(`
		${skeys.map(encodeParseExpr).join(',')} = sympy.symbols('${skeys.map(encodeParseExpr).join(' ')}')
	`);

	expressionStr = encodeParseExpr(expressionStr);
	const result = pyodide.runPython(`
		eq = sympy.S("${expressionStr}", locals=_clash)
		eq.evalf(subs={${subs.join(', ')}})
	`);

	return revertParseExpr(result.toString());
};

const parseExpression = (expr: string) => {
	const output = {
		mathml: '',
		pmathml: '',
		latex: '',
		freeSymbols: [] as any[]
	};

	if (!expr || expr.length === 0) {
		return output;
	}

	// Special cases
	expr = encodeParseExpr(expr);

	// function to convert expression to presentation mathml
	let result = pyodide.runPython(`
		eq = sympy.S("${expr}", locals=_clash)
		sympy.mathml(eq, printer="presentation")
	`);

	// manually replace <mfenced> due to browser deprecation
	// https://developer.mozilla.org/en-US/docs/Web/MathML/Element/mfenced
	result = result.replaceAll('<mfenced>', '<mo>(</mo><mrow>');
	result = result.replaceAll('</mfenced>', '</mrow><mo>)</mo>');

	// add mathml top level element tags
	output.pmathml = `<math xmlns="http://www.w3.org/1998/Math/MathML">${result}</math>`;

	// function to convert expression to barebone mathml
	result = pyodide.runPython(`
		eq = sympy.S("${expr}", locals=_clash)
		sympy.mathml(eq)
	`);
	output.mathml = result;

	result = pyodide.runPython(`
		eq = sympy.S("${expr}", locals=_clash)
		latex(eq)
	`);
	output.latex = result;

	result = pyodide.runPython(`
		eq = sympy.S("${expr}", locals=_clash)
		list(map(lambda x: x.name, eq.free_symbols))
	`);
	output.freeSymbols = result.toJs();

	output.latex = revertParseExpr(output.latex);
	output.mathml = revertParseExpr(output.mathml);
	output.pmathml = revertParseExpr(output.pmathml);
	output.freeSymbols = output.freeSymbols.map(revertParseExpr);

	return output;
};

const substituteExpression = (expressionStr: string, newVar: string, oldVar: string) => {
	const result: PyProxy = pyodide.runPython(`
		eq = sympy.S("${expressionStr}", locals=_clash)
		new_eq = eq.replace(${oldVar}, ${newVar})
		serialize_expr(new_eq)
	`);
	return {
		latex: result.get('latex'),
		mathml: result.get('mathml'),
		str: result.get('str')
	};
};

const removeExpressions = (expressionStr: string, v: string[]) => {
	// Register
	pyodide.runPython(`
		${v.join(',')} = sympy.symbols('${v.join(' ')}')
	`);

	// convert to python tuples
	const tuples = v.map((d) => `(${d}, 0)`);
	const p = `[${tuples.join(',')}]`;

	const result: PyProxy = pyodide.runPython(`
		eq = sympy.S("${expressionStr}", locals=_clash)
		new_eq = eq.subs(${p})
		serialize_expr(new_eq)
	`);

	// const result: PyProxy = pyodide.runPython(`
	// 	eq = sympy.S("${expressionStr}", locals=_clash)
	// 	new_eq = sq.subs(${v}, 0)
	// 	serialize_expr(new_eq)
	// `);
	//
	return {
		latex: result.get('latex'),
		mathml: result.get('mathml'),
		str: result.get('str')
	};
};

const runPython = (code: string) => {
	const result: PyProxy = pyodide.runPython(code);
	return result.toJs();
};

const getRankingScores = (data: any[], outcomesofInterest: string[], parametersOfInterest: string[]) => {
	data = pyodide.toPy(data);
	outcomesofInterest = pyodide.toPy(outcomesofInterest);
	parametersOfInterest = pyodide.toPy(parametersOfInterest);

	const result: PyProxy = pyodide.runPython(`
		get_ranking_scores(${data}, ${outcomesofInterest}, ${parametersOfInterest})
	`);

	const res = result.get('scores_by_ooi').toJs();
	return res;
};

const map = new Map<string, Function>();
map.set('parseExpression', parseExpression);
map.set('substituteExpression', substituteExpression);
map.set('evaluateExpression', evaluateExpression);
map.set('removeExpressions', removeExpressions);
map.set('runPython', runPython);
map.set('getRankingScores', getRankingScores);

onmessage = function (e) {
	const { action, params } = e.data;

	const func = map.get(action);
	if (func) {
		// eslint-disable-next-line
		return postMessage(func.apply(null, params));
	}
	return '';
};
