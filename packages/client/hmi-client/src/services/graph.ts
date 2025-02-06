/**
 * Provides graph rendering utilities for computational-like graphs
 */
import _ from 'lodash';
import * as d3 from 'd3';
import dagre from 'dagre';
import graphScaffolder, { IGraph, INode, IEdge } from '@graph-scaffolder/index';
import type { Position } from '@/types/common';
import OrthogonalConnector, { Side } from '@/utils/ortho-router';
import { getAStarPath } from '@graph-scaffolder/core';

export type D3SelectionINode<T> = d3.Selection<d3.BaseType, INode<T>, null, any>;
export type D3SelectionIEdge<T> = d3.Selection<d3.BaseType, IEdge<T>, null, any>;

export enum NodeType {
	State = 'state',
	Transition = 'transition',
	Observable = 'observable'
}

export const pathFn = d3
	.line<{ x: number; y: number }>()
	.x((d) => d.x)
	.y((d) => d.y);

function interpolatePointsForCurve(a: Position, b: Position): Position[] {
	const controlXOffset = 50;
	return [a, { x: a.x + controlXOffset, y: a.y }, { x: b.x - controlXOffset, y: b.y }, b];
}

// Stand-alone edge rerouting given a set of node positions and their
// connections.
interface ExtRect {
	left: number;
	top: number;
	width: number;
	height: number;
	cx: number;
	cy: number;
}

enum EdgeProportion {
	None,
	Source,
	Target,
	Both
}

/**
 * Options
 * - edgeProportion determines if edge start/end should be placed proportionally distance away
 * from each other.
 * - adpativeRoute determines if the side where edge start/end shold be fixed or adaptive
 *
 * */
const routeOptions = {
	edgeProportion: EdgeProportion.None,
	edgeAdaptiveSide: false
};

interface IPoint {
	x: number;
	y: number;
}

// A faster but not great looking edges
export const rerouteEdgesFast = (nodes: INode<any>[], edges: IEdge<any>[]) => {
	function basicCollisionFn(p: IPoint) {
		const buffer = 0;
		for (let i = 0; i < nodes.length; i++) {
			const checkingObj = nodes[i];
			if (p.x >= checkingObj.x - buffer && p.x <= checkingObj.x + checkingObj.width + buffer) {
				if (p.y >= checkingObj.y - buffer && p.y <= checkingObj.y + checkingObj.height + buffer) {
					return true;
				}
			}
		}
		return false;
	}

	const nodeMap: Map<string, any> = new Map();
	nodes.forEach((node) => {
		nodeMap.set(node.id, {
			x: node.x,
			y: node.y
		});
	});

	edges.forEach((edge: IEdge<any>) => {
		const a = nodeMap.get(edge.source);
		const b = nodeMap.get(edge.target);
		const path = getAStarPath(a, b, { collider: basicCollisionFn });
		edge.points = path;
	});
};

export const rerouteEdges = (nodes: INode<any>[], edges: IEdge<any>[]) => {
	const nodeMap: Map<string, ExtRect> = new Map();
	nodes.forEach((node) => {
		nodeMap.set(node.id, {
			left: node.x - 0.5 * node.width,
			top: node.y - 0.5 * node.height,
			width: node.width,
			height: node.height,

			// extras for side calculation
			cx: node.x,
			cy: node.y
		});
	});

	const obstacles = nodes.map((node) => nodeMap.get(node.id) as any);

	// Figure out how many edges extends out of each given nodes for edge placement.
	// eg if there are 3 edges coming out of node-A, then the placement of these
	// edges are (edge-i/num+1) => [1/4, 2/4, 3/4]
	const edgeSourceMap: Map<string, number> = new Map();
	const edgeTargetMap: Map<string, number> = new Map();
	edges.forEach((edge: IEdge<any>) => {
		if (edge.data.isObservable) return;
		if (!edgeSourceMap.has(edge.source)) {
			edgeSourceMap.set(edge.source, 0);
		}
		if (!edgeTargetMap.has(edge.target)) {
			edgeTargetMap.set(edge.target, 0);
		}
		edgeSourceMap.set(edge.source, 1 + (edgeSourceMap.get(edge.source) as number));
		edgeTargetMap.set(edge.target, 1 + (edgeTargetMap.get(edge.target) as number));
	});

	const sourceNodeCounter: Map<string, number> = new Map();
	const targetNodeCounter: Map<string, number> = new Map();

	edges.forEach((edge: IEdge<any>) => {
		if (edge.data.isObservable) return;
		if (!sourceNodeCounter.has(edge.source)) {
			sourceNodeCounter.set(edge.source, 0);
		}
		if (!targetNodeCounter.has(edge.target)) {
			targetNodeCounter.set(edge.target, 0);
		}

		let sourceCounter = sourceNodeCounter.get(edge.source) as number;
		let targetCounter = targetNodeCounter.get(edge.target) as number;

		const sourceDenominator = edgeSourceMap.get(edge.source) as number;
		const targetDenominator = edgeTargetMap.get(edge.target) as number;
		sourceCounter++;
		targetCounter++;

		let sideA: Side = 'right';
		let sideB: Side = 'left';

		const shapeA = nodeMap.get(edge.source) as ExtRect;
		const shapeB = nodeMap.get(edge.target) as ExtRect;

		if (routeOptions.edgeAdaptiveSide) {
			if (shapeA.cx >= shapeB.cx) {
				sideA = 'left';
				sideB = 'right';
			} else {
				sideA = 'right';
				sideB = 'left';
			}
		}

		const orthoOptions = {
			pointA: { shape: shapeA, side: sideA, distance: 0.5 },
			pointB: { shape: shapeB, side: sideB, distance: 0.5 },
			shapeMargin: 20,
			globalBoundsMargin: 10,
			globalBounds: { left: -2000, top: -2000, width: 4000, height: 4000 },
			obstacles
		};

		if (routeOptions.edgeProportion === EdgeProportion.Both) {
			orthoOptions.pointA.distance = sourceCounter / (sourceDenominator + 1);
			orthoOptions.pointB.distance = targetCounter / (targetDenominator + 1);
		} else if (routeOptions.edgeProportion === EdgeProportion.Source) {
			orthoOptions.pointA.distance = sourceCounter / (sourceDenominator + 1);
			orthoOptions.pointB.distance = 0.5;
		} else if (routeOptions.edgeProportion === EdgeProportion.Target) {
			orthoOptions.pointA.distance = 0.5;
			orthoOptions.pointB.distance = targetCounter / (targetDenominator + 1);
		} else {
			orthoOptions.pointA.distance = 0.5;
			orthoOptions.pointB.distance = 0.5;
		}
		const path = OrthogonalConnector.route(orthoOptions);
		edge.points = path;

		// Just in case algorithm fails to return, set manual edge points so
		// at least it renders
		if (edge.points.length === 0) {
			console.warn(`failed ortho edge ${edge.source} => ${edge.target}`);
			const startX = sideA === 'right' ? shapeA.cx + 0.5 * shapeA.width : shapeA.cx - 0.5 * shapeA.width;
			const startY = shapeA.cy;

			const endX = sideB === 'right' ? shapeB.cx + 0.5 * shapeB.width : shapeB.cx - 0.5 * shapeB.width;
			const endY = shapeA.cy;

			edge.points.push({
				x: startX,
				y: startY
			});
			edge.points.push({
				x: startX + 0.5 * (endX - startX),
				y: startY + 0.5 * (endY - startY) + 40
			});
			edge.points.push({
				x: endX,
				y: endY
			});
		}

		sourceNodeCounter.set(edge.source, sourceCounter);
		targetNodeCounter.set(edge.target, targetCounter);
	});
};

export const runDagreLayout = <V, E>(graphData: IGraph<V, E>, lr: boolean = true): IGraph<V, E> => {
	const g = new dagre.graphlib.Graph({ compound: true });
	g.setGraph({});
	g.setDefaultEdgeLabel(() => ({}));

	let observableAmount = 0;
	graphScaffolder.traverseGraph(graphData, (node: INode<any>) => {
		if (node.data?.type === NodeType.Observable) observableAmount++;
		g.setNode(node.id, {
			label: node.label,
			width: node.width ?? 50,
			height: node.height ?? 50
		});
		node.nodes.forEach((child) => g.setParent(child.id, node.id));
	});

	// Set state/transitions edges
	// Ignore
	// - observable edges, they are realized at interaction time
	// - controller edges, they are routed after initial layout because they can
	//   intefere with the general topological structure
	graphData.edges.forEach((edge: IEdge<any>) => {
		if (edge.data?.isObservable) return;
		if (edge.data?.isController) return;
		g.setEdge(edge.source, edge.target);
	});

	if (lr === true) {
		g.graph().rankDir = 'LR';
		g.graph().nodesep = 100;
		g.graph().ranksep = 125;
	}

	dagre.layout(g);

	let mostRightNodeX = 0;
	let lowestNodeY = 0;
	let highestNodeY = 0;
	let currentObservableY = 0;
	let isAddingObservables = false;
	graphScaffolder.traverseGraph(graphData, (node: INode<any>) => {
		let n = g.node(node.id);
		const pid = g.parent(node.id);
		// Determine bounds from state and transition nodes
		// Observables are added to the end graphData.nodes array in convertToIGraph so assume that's the order
		if (node.data?.type !== NodeType.Observable) {
			if (n.x > mostRightNodeX) mostRightNodeX = n.x;
			if (n.y < lowestNodeY) lowestNodeY = n.y;
			if (n.y > highestNodeY) highestNodeY = n.y;
		}
		// Determine observable node (custom) placement
		else {
			if (!isAddingObservables) {
				isAddingObservables = true;
				mostRightNodeX += 150;
				const midPointY = (highestNodeY + lowestNodeY) / 2;
				const observablesHeight = observableAmount * n.height;
				currentObservableY = midPointY - observablesHeight / 2;
			}
			g.setNode(node.id, {
				x: mostRightNodeX,
				y: currentObservableY,
				width: node.width,
				height: node.height
			});
			n = g.node(node.id);
			currentObservableY += 100;
		}
		// Place node
		node.x = n.x;
		node.y = n.y;
		if (pid) {
			node.x -= g.node(pid).x;
			node.y -= g.node(pid).y;
		}
	});

	graphData.edges.forEach((edge: IEdge<any>) => {
		// Set observable (custom) edges here
		if (edge.data?.isObservable) {
			const sourceNode = g.node(edge.source);
			const targetNode = g.node(edge.target);
			if (!sourceNode || !targetNode) return;
			g.setEdge(edge.source, edge.target, {
				points: interpolatePointsForCurve(sourceNode, targetNode)
			});
		}
		const e = g.edge(edge.source, edge.target);
		if (e?.points) edge.points = _.cloneDeep(e.points);
	});

	// HACK: multi-edges
	const dupe: Set<string> = new Set();
	for (let idx = 0; idx < graphData.edges.length; idx++) {
		const edge = graphData.edges[idx];
		const hash = `${edge.source};${edge.target}`;
		if (dupe.has(hash) && edge.points.length > 2) {
			for (let i = 1; i < edge.points.length - 1; i++) {
				edge.points[i].y -= 25;
			}
		}
		dupe.add(hash);
	}

	// Find new width and height
	if (graphData.nodes.length > 0) {
		let minX = Number.MAX_VALUE;
		let maxX = Number.MIN_VALUE;
		let minY = Number.MAX_VALUE;
		let maxY = Number.MIN_VALUE;
		graphData.nodes.forEach((node) => {
			if (node.x - 0.5 * node.width < minX) minX = node.x - 0.5 * node.width;
			if (node.x + 0.5 * node.width > maxX) maxX = node.x + 0.5 * node.width;
			if (node.y - 0.5 * node.height < minY) minY = node.y - 0.5 * node.height;
			if (node.y + 0.5 * node.height > maxY) maxY = node.y + 0.5 * node.height;
		});

		// manual route controller edges
		const controllerEdges = graphData.edges.filter((edge: IEdge<any>) => edge.data?.isController === true);

		// Effectively disabling fast-version, DC Feb 2025
		if (controllerEdges.length < 99999) {
			rerouteEdges(graphData.nodes, controllerEdges);
		} else {
			rerouteEdgesFast(graphData.nodes, controllerEdges);
		}

		// FIXME: temp hack, need to optimize OrthogonalConnector
		// Rearrange non-controller edges to make them nicer
		const nonControllerEdges = graphData.edges.filter((edge: IEdge<any>) => edge.data?.isController !== true);
		if (nonControllerEdges.length < 25) {
			rerouteEdges(graphData.nodes, nonControllerEdges);
		}

		// Give the bounds a little extra buffer
		const buffer = 10;
		maxX += buffer;
		maxY += buffer;
		minX -= buffer;
		minY -= buffer;

		graphData.width = Math.abs(maxX - minX);
		graphData.height = Math.abs(maxY - minY);
	}
	return graphData;
};
