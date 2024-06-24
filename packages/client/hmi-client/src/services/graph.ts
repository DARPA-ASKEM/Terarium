/**
 * Provides graph rendering utilities for computational-like graphs
 */
import _ from 'lodash';
import * as d3 from 'd3';
import dagre from 'dagre';
import graphScaffolder, { IGraph, INode, IEdge } from '@graph-scaffolder/index';
import type { Position } from '@/types/common';

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
	graphData.edges.forEach((edge: IEdge<any>) => {
		if (edge.data?.isObservable) return;
		g.setEdge(edge.source, edge.target);
	});

	if (lr === true) {
		g.graph().rankDir = 'LR';
		g.graph().nodesep = 100;
		g.graph().ranksep = 100;
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
			g.setEdge(edge.source, edge.target, {
				points: interpolatePointsForCurve(g.node(edge.source), g.node(edge.target))
			});
		}
		const e = g.edge(edge.source, edge.target);
		edge.points = _.cloneDeep(e.points);
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
