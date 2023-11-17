/**
 * Provides graph rendering utilities for computational-like graphs
 */
import _ from 'lodash';
import * as d3 from 'd3';
import dagre from 'dagre';
import graphScaffolder, { IGraph, INode, IEdge } from '@graph-scaffolder/index';

export type D3SelectionINode<T> = d3.Selection<d3.BaseType, INode<T>, null, any>;
export type D3SelectionIEdge<T> = d3.Selection<d3.BaseType, IEdge<T>, null, any>;

export const pathFn = d3
	.line<{ x: number; y: number }>()
	.x((d) => d.x)
	.y((d) => d.y);

export const runDagreLayout = <V, E>(graphData: IGraph<V, E>, lr: boolean = true): IGraph<V, E> => {
	const g = new dagre.graphlib.Graph({ compound: true });
	g.setGraph({});
	g.setDefaultEdgeLabel(() => ({}));
	let nodeWidth;
	let nodeHeight;

	graphScaffolder.traverseGraph(graphData, (node: INode<V>) => {
		if (node.width && node.height) {
			g.setNode(node.id, {
				label: node.label,
				width: node.width,
				height: node.height,
				x: node.x,
				y: node.y
			});
			nodeWidth = node.width;
			nodeHeight = node.height;
		} else {
			g.setNode(node.id, { label: node.label, x: node.x, y: node.y });
			nodeWidth = node.width;
			nodeHeight = node.height;
		}
		if (!_.isEmpty(node.nodes)) {
			// eslint-disable-next-line
			for (const child of node.nodes) {
				g.setParent(child.id, node.id);
			}
		}
	});

	// eslint-disable-next-line
	for (const edge of graphData.edges) {
		g.setEdge(edge.source, edge.target);
	}

	if (lr === true) {
		g.graph().rankDir = 'LR';
		g.graph().nodesep = 100;
		g.graph().ranksep = 100;
	}

	dagre.layout(g);

	graphScaffolder.traverseGraph(graphData, (node) => {
		const n = g.node(node.id);
		node.width = n.width;
		node.height = n.height;
		node.x = n.x;
		node.y = n.y;

		const pid = g.parent(node.id);
		if (pid) {
			node.x -= g.node(pid).x;
			node.y -= g.node(pid).y;
		}
	});

	// eslint-disable-next-line
	for (const edge of graphData.edges) {
		const e = g.edge(edge.source, edge.target);
		edge.points = _.cloneDeep(e.points);
	}

	// HACK: multi-edges
	const dupe: Set<string> = new Set();
	for (let idx = 0; idx < graphData.edges.length; idx++) {
		const edge = graphData.edges[idx];
		const hash = `${edge.source};${edge.target}`;
		if (dupe.has(hash)) {
			if (edge.points.length > 2) {
				for (let i = 1; i < edge.points.length - 1; i++) {
					edge.points[i].y -= 25;
				}
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
			if (node.x < minX) minX = node.x;
			if (node.x > maxX) maxX = node.x;
			if (node.y < minY) minY = node.y;
			if (node.y > maxY) maxY = node.y;
		});

		graphData.width = Math.abs(maxX - minX) + 2 * nodeWidth;
		graphData.height = Math.abs(maxY - minY) + 2 * nodeHeight;
	}

	return graphData;
};
