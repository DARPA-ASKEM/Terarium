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
await pyodide.loadPackage('sympy');
pyodide.runPython('import sympy');
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

// Bootstrap
const keys = Object.keys(variableMap);
pyodide.runPython(`
    ${keys.join(',')} = sympy.symbols('${keys.join(' ')}')
`);

postMessage(true);

const encodeParseExpr = (v: string) => {
	let expr = v.toString().replaceAll('lambda', 'XXlambdaXX');
	expr = expr.replaceAll('Ci', 'XXCiXX');
	expr = expr.replaceAll('S', 'XXSXX');
	return expr;
};

// Reverse special cases
const revertParseExpr = (v: string) => {
	let resultStr = v.replaceAll('XXlambdaXX', 'lambda');
	resultStr = resultStr.replaceAll('XXSXX', 'S');
	resultStr = resultStr.replaceAll('XXCiXX', 'Ci');
	return resultStr;
};

const evaluateExpression = (expressionStr: string, symbolsTable: Object) => {
	const subs: any[] = [];
	Object.keys(symbolsTable).forEach((key) => {
		subs.push(`${key}: ${symbolsTable[key]}`);
	});

	const skeys = Object.keys(symbolsTable);
	pyodide.runPython(`
		${skeys.join(',')} = sympy.symbols('${skeys.join(' ')}')
	`);

	const result = pyodide.runPython(`
		eq = sympy.S("${expressionStr}", locals=_clash)
		eq.evalf(subs={${subs.join(', ')}})
	`);
	return result.toString();
};

const parseExpression = (expr: string) => {
	const output = {
		mathml: '',
		pmathml: '',
		latex: '',
		freeSymbols: []
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

const map = new Map<string, Function>();
map.set('parseExpression', parseExpression);
map.set('substituteExpression', substituteExpression);
map.set('evaluateExpression', evaluateExpression);
map.set('removeExpressions', removeExpressions);
map.set('runPython', runPython);

onmessage = function (e) {
	const { action, params } = e.data;

	const func = map.get(action);
	if (func) {
		// eslint-disable-next-line
		return postMessage(func.apply(null, params));
	}
	return '';
};
