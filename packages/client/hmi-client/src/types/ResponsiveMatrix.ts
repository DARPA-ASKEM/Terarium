import { Texture, Resource } from 'pixi.js';
import { Selection } from 'd3';

export type D3SvgSelection = Selection<SVGSVGElement, unknown, null, undefined>;

export type ParamMinMax = {
	[key: string | number]: number;
};

export type CellData = {
	col: number;
	row: number;
	_idx: number;
	[key: string | number]: any; // arbitrary cell data
};

export enum CellType {
	BAR = 'ResponsiveCellBarContainer',
	LINE = 'ResponsiveCellLineContainer'
}

export enum CellStatus {
	NONE,
	SELECTED
}

export type SelectedCell = [number, number, number, number];

export type SelectedCellData = {
	[key: string | number]: number[];
};

export enum SelectedCellValue {
	START_ROW,
	START_COL,
	END_ROW,
	END_COL
}

export type LabelData = {
	value: string | number;
	altText?: string;
};

export type DataConfig = {
	dataRow: LabelData[];
	dataCol: LabelData[];
};

export type RowColConfig = {
	borderEnabled: boolean;
	borderWidth: number;
	// TODO
	// labelFormatterFn: (val :any, idx: number) => string
	// labelAltFn?: (val: any, idx: number) => string,
};

export type VisConfig = {
	row: RowColConfig;
	col: RowColConfig;
};

export type Uniforms = {
	// screen data
	uScreenWidth: number;
	uScreenHeight: number;

	// geometry/viewport data
	uWorldWidth: number;
	uWorldHeight: number;
	uViewportWorldWidth: number;
	uViewportWorldHeight: number;

	// grid settings
	uGridDisplayBorder: boolean;
	uGridRowDisplayLim: number;
	uGridRowDisplayTransition: number;
	uGridColDisplayLim: number;
	uGridColDisplayTransition: number;

	// row/col data
	uNumRow: number;
	uNumCol: number;
	uMicroElDim: { x: number; y: number };
	uMicroRow: Texture<Resource>;
	uMicroCol: Texture<Resource>;

	// cell element color data
	uColor: Texture;
};

export enum CursorModes {
	SELECT,
	CAMERA
}
