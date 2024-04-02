export enum DocumentSource {
	XDD = 'xDD',
	Terarium = 'Terarium'
}

export enum DatasetSource {
	Terarium = 'Terarium',
	ESGF = 'ESGF'
}

export type Source = DocumentSource | DatasetSource;
