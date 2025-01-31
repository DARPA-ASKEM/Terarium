export default class PyodideController {
	private _isReady = false;

	private _isBusy = false;

	private worker: Promise<Worker>;

	private taskQueue: any[] = [];

	constructor() {
		this.worker = new Promise((resolve, reject) => {
			const worker = new Worker(new URL('./PyodideWorker.ts', import.meta.url), {
				type: 'module'
			});
			worker.onmessage = (e: any) => {
				if (e.data) {
					this._isReady = true;
					resolve(worker);
				} else {
					console.error('Pyodide Init Error');
					reject();
				}
			};
		});
	}

	get isReady() {
		return this._isReady;
	}

	get isBusy() {
		return this._isBusy;
	}

	async parseExpression(expr: string): Promise<{
		mathml: string;
		pmathml: string;
		latex: string;
		freeSymbols: string[];
	}> {
		return new Promise((...promise) => {
			this.taskQueue.push({
				action: 'parseExpression',
				params: [expr],
				promise
			});
			this.queueTask();
		});
	}

	async evaluateExpression(expr: string, symbolTable: object): Promise<string> {
		return new Promise((...promise) => {
			this.taskQueue.push({
				action: 'evaluateExpression',
				params: [expr, symbolTable],
				promise
			});
			this.queueTask();
		});
	}

	async removeExpressions(expr: string, removeList: string[]): Promise<string> {
		return new Promise((...promise) => {
			this.taskQueue.push({
				action: 'removeExpressions',
				params: [expr, removeList],
				promise
			});
			this.queueTask();
		});
	}

	async runPython(code: string) {
		return new Promise((...promise) => {
			this.taskQueue.push({
				action: 'runPython',
				params: [code],
				promise
			});
			this.queueTask();
		});
	}

	async getRankingScores(results: any[], outcomesofInterest: string[], parametersOfInterest: string[]) {
		return new Promise<Map<string, Map<string, number>>>((...promise) => {
			this.taskQueue.push({
				action: 'getRankingScores',
				params: [JSON.stringify(results), JSON.stringify(outcomesofInterest), JSON.stringify(parametersOfInterest)],
				promise
			});
			this.queueTask();
		});
	}

	private async queueTask() {
		const worker = await this.worker;

		if (this.taskQueue.length && !this._isBusy) {
			this._isBusy = true;
			const { action, params, promise } = this.taskQueue.pop();
			worker.onmessage = (e) => {
				this._isBusy = false;
				this.queueTask();
				promise[0](e.data);
			};
			worker.onerror = (e) => {
				console.error(e);
				this._isBusy = false;
				this.queueTask();
			};
			worker.postMessage({
				action,
				params
			});
		}
	}
}

// Single instance for the app
export const pythonInstance = new PyodideController();
