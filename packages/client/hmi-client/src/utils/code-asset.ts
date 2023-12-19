import { getCodeFileAsText } from '@/services/code';
import { Code, ProgrammingLanguage } from '@/types/Types';

export enum CodeBlockType {
	INPUT = 'input'
}
export interface CodeBlock {
	filename?: string;
	block?: string;
	codeLanguage: ProgrammingLanguage;
	codeContent: string;
	name: string;
	includeInProcess?: boolean;
	type?: CodeBlockType;
}

export const extractDynamicRows = (range: string) => {
	const match = range.match(/L(\d+)-L(\d+)/) || [];
	const startRow = parseInt(match[1], 10) - 1;
	const endRow = parseInt(match[2], 10) - 1;
	return { startRow, endRow };
};

export const extractCodeLines = (code: string, start: number, end: number) => {
	const lines = code.split('\n');

	// Ensure startLine and endLine are within valid range
	start = Math.max(0, start - 1);
	end = Math.min(lines.length, end);

	// Extract lines between startLine and endLine (inclusive)
	const extractedCode = lines.slice(start, end + 1).join('\n');

	return extractedCode;
};

export const getCodeBlocks = async (code: Code): Promise<CodeBlock[]> => {
	if (!code.id || !code.files) return [];

	const filteredFiles = Object.entries(code.files).filter(
		(fileEntry) => fileEntry[1].dynamics?.block?.length > 0
	);

	const promises = filteredFiles.map(async (fileEntry) => {
		const codeContent = (await getCodeFileAsText(code.id!, fileEntry[0])) ?? '';

		// Assuming fileEntry[1].dynamics.block is an array
		const inputCodeBlocksForFile = fileEntry[1].dynamics.block.map((block) => {
			const { startRow, endRow } = extractDynamicRows(block);
			const codeSnippet = extractCodeLines(codeContent, startRow, endRow);

			return {
				filename: fileEntry[0],
				block,
				codeLanguage: fileEntry[1].language,
				codeContent: codeSnippet ?? '',
				name: 'Code Block',
				includeInProcess: true,
				type: CodeBlockType.INPUT
			};
		});

		return inputCodeBlocksForFile;
	});
	const inputCodeBlocksArrays = await Promise.all(promises);

	// Flatten the arrays since map returns an array of arrays
	return inputCodeBlocksArrays.flat();
};
