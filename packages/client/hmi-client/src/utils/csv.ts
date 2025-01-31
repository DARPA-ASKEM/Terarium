import { autoType, csvParse } from 'd3';
import type { CsvAsset } from '@/types/Types';

export const parseCsvAsset = (csvAsset: CsvAsset) => {
	if (!csvAsset) return [];
	const csv = [csvAsset.headers, ...csvAsset.csv];
	const csvRaw = csv.map((d) => d.join(',')).join('\n');
	const parsedCsv = csvParse(csvRaw, autoType);
	return parsedCsv;
};
