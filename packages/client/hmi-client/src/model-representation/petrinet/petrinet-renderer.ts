import _ from 'lodash';
import * as d3 from 'd3';
import { BasicRenderer, INode, IEdge } from '@graph-scaffolder/index';
import { D3SelectionINode, D3SelectionIEdge } from '@/services/graph';
import { pointOnPath } from '@/utils/svg';
import { Model } from '@/types/Types';
import * as petrinetService from '@/model-representation/petrinet/petrinet-service';

export interface NodeData {
	type: string;
	uid?: string | number;
}

export interface EdgeData {
	numEdges: number;
}

export enum NodeType {
	State = 'state',
	Transition = 'transition'
}

const MARKER_VIEWBOX = '-5 -5 10 10';
const ARROW = 'M 0,-3.25 L 5 ,0 L 0,3.25';
const pathFn = d3
	.line<{ x: number; y: number }>()
	.x((d) => d.x)
	.y((d) => d.y)
	.curve(d3.curveBasis);

const EDGE_COLOR = 'var(--petri-lineColor)';
const HIGHLIGHTEDSTROKECOLOUR = 'var(--primary-color)';
const EDGE_OPACITY = 0.5;

const HANDLE_SIZE = 4;
const HANDLE_SIZE_HOVER = 8;

export class PetrinetRenderer extends BasicRenderer<NodeData, EdgeData> {
	nodeSelection: D3SelectionINode<NodeData> | null = null;

	edgeSelection: D3SelectionIEdge<EdgeData> | null = null;

	editMode: boolean = false;

	initialize(element: HTMLDivElement): void {
		super.initialize(element);

		d3.select(this.svgEl)
			.style('border', '4px solid transparent')
			.style('background', 'var(--surface-0')
			.style('border-radius', 'var(--border-radius) 0px 0px var(--border-radius)');
	}

	setupDefs() {
		const svg = d3.select(this.svgEl);

		// Clean up
		svg.select('defs').selectAll('.edge-marker-end').remove();

		// Arrow defs
		svg
			.select('defs')
			.append('marker')
			.classed('edge-marker-end', true)
			.attr('id', 'arrowhead')
			.attr('viewBox', MARKER_VIEWBOX)
			.attr('refX', 6)
			.attr('refY', 0)
			.attr('orient', 'auto')
			.attr('markerWidth', 20)
			.attr('markerHeight', 20)
			.attr('markerUnits', 'userSpaceOnUse')
			.attr('xoverflow', 'visible')
			.append('svg:path')
			.attr('d', ARROW)
			.style('fill', EDGE_COLOR)
			.style('fill-opacity', EDGE_OPACITY)
			.style('stroke', 'none');
	}

	setEditMode(v: boolean) {
		this.editMode = v;

		const svg = d3.select(this.svgEl);
		if (this.editMode) {
			svg.style('border', '4px solid var(--primary-color)');
		} else {
			svg.style('border', '4px solid transparent');
		}
	}

	renderNodes(selection: D3SelectionINode<NodeData>) {
		const species = selection.filter((d) => d.data.type === NodeType.State);
		const transitions = selection.filter((d) => d.data.type === NodeType.Transition);

		// transitions
		transitions
			.append('rect')
			.classed('shape selectableNode', true)
			.attr('width', (d) => d.width)
			.attr('height', (d) => d.height)
			.attr('y', (d) => -d.height * 0.5)
			.attr('x', (d) => -d.width * 0.5)
			.attr('rx', '6')
			.attr('ry', '6')
			.style('fill', 'var(--petri-nodeFill')
			.style('cursor', 'pointer')
			.attr('stroke', 'var(--petri-nodeBorder)')
			.attr('stroke-width', 1);

		function handleMouseOver(evt: MouseEvent) {
			d3.select(evt.target as any)
				.transition()
				.duration(100)
				.attr('r', HANDLE_SIZE_HOVER);
		}

		function handleMouseOut(evt: MouseEvent) {
			d3.select(evt.target as any)
				.transition()
				.duration(100)
				.attr('r', HANDLE_SIZE);
		}

		// transitions drag handles
		const transitionsHandles = [
			...transitions.append('circle').attr('cx', (d) => d.width * 0.75),
			...transitions.append('circle').attr('cy', (d) => d.height * 0.75),
			...transitions.append('circle').attr('cx', (d) => -d.width * 0.75),
			...transitions.append('circle').attr('cy', (d) => -d.height * 0.75)
		];

		d3.selectAll(transitionsHandles)
			.classed('shape no-drag', true)
			.attr('r', HANDLE_SIZE)
			.attr('fill', 'var(--primary-color)')
			.attr('stroke', 'none')
			.style('cursor', 'pointer')
			.style('opacity', 0)
			.on('mouseover', handleMouseOver)
			.on('mouseout', handleMouseOut);

		// transitions text
		transitions
			.append('text')
			.attr('y', () => 5)
			.style('text-anchor', 'middle')
			.style('paint-order', 'stroke')
			.style('stroke', '#FFF')
			.style('stroke-width', '3px')
			.style('stroke-linecap', 'butt')
			.style('fill', 'var(--text-color-primary')
			.style('pointer-events', 'none')
			.text((d) => d.label);

		// species
		species
			.append('circle')
			.classed('shape selectableNode', true)
			.attr('r', (d) => 0.55 * d.width) // FIXME: need to adjust edge from sqaure mapping to circle
			.attr('fill', 'var(--petri-nodeFill)')
			.attr('stroke', 'var(--petri-nodeBorder)')
			.attr('stroke-width', 1)
			.style('cursor', 'pointer');

		// species drag handles
		const speciesHandles = [
			...species.append('circle').attr('cx', (d) => d.width * 0.75),
			...species.append('circle').attr('cy', (d) => d.height * 0.75),
			...species.append('circle').attr('cx', (d) => -d.width * 0.75),
			...species.append('circle').attr('cy', (d) => -d.height * 0.75)
		];

		d3.selectAll(speciesHandles)
			.classed('shape no-drag', true)
			.attr('r', HANDLE_SIZE)
			.attr('fill', 'var(--primary-color)')
			.attr('stroke', 'none')
			.style('cursor', 'pointer')
			.style('opacity', 0)
			.on('mouseover', handleMouseOver)
			.on('mouseout', handleMouseOut);

		// species text
		species
			.append('text')
			.attr('y', () => 5)
			.style('text-anchor', 'middle')
			.style('paint-order', 'stroke')
			.style('stroke', '#FFF')
			.style('stroke-width', '3px')
			.style('stroke-linecap', 'round')
			.style('fill', 'var(--text-color-primary')
			.style('pointer-events', 'none')
			.text((d) => d.label);
	}

	renderEdges(selection: D3SelectionIEdge<EdgeData>) {
		selection
			.append('path')
			.attr('d', (d) => pathFn(d.points))
			.style('fill', 'none')
			.style('stroke', EDGE_COLOR)
			.style('stroke-opacity', EDGE_OPACITY)
			.style('stroke-width', 3)
			.attr('marker-end', 'url(#arrowhead)');

		this.updateMultiEdgeLabels();
	}

	updateMultiEdgeLabels() {
		const selection = this.chart?.selectAll('.edge') as D3SelectionIEdge<EdgeData>;
		const multiEdges = selection.filter((d) => (d.data && d.data.numEdges > 1) as boolean);
		multiEdges.selectAll('.multi-edge-label').remove();

		multiEdges.each((_d, index, group) => {
			const edgeSelection = d3.select<any, IEdge<EdgeData>>(group[index]);
			const point = pointOnPath(edgeSelection.select('path').node() as any, 0.5);
			edgeSelection
				.append('text')
				.classed('multi-edge-label', true)
				.attr('x', point.x)
				.attr('y', point.y + 6)
				.style('font-size', 18)
				.style('paint-order', 'stroke')
				.style('stroke', 'var(--gray-50)')
				.style('stroke-width', '6px')
				.style('stroke-linecap', 'butt')
				.style('fill', 'var(--text-color-primary')
				.text((d) => d.data?.numEdges as number);
		});
	}

	selectNodeEdit(selection: D3SelectionINode<NodeData>) {
		selection.selectAll('.no-drag').attr('stroke-width', 3);
		selection.select('text').style('fill-opacity', 0);

		// Add in a input textbox to change the name
		const w = selection.datum().width;
		const h = selection.datum().width;

		selection
			.append('foreignObject')
			.attr('x', -0.5 * w)
			.attr('y', -0.5 * h)
			.attr('width', w)
			.attr('height', h)
			.attr('style', 'position:relative')
			.append('xhtml:input')
			.attr('type', 'text')
			.attr(
				'style',
				`width:${
					w * 0.8
				}px; height:30px; background: var(--petri-inputBox); border-radius:var(--border-radius); border: 2px solid transparent; text-align:center; position: absolute; top:50%; left:50%; transform: translate(-50%, -50%); padding-left:2px; padding-right:2px;`
			)
			.attr('value', selection.datum().label);
	}

	deselectNode(selection: D3SelectionINode<NodeData>) {
		if (!this.editMode) return;
		selection.selectAll('.no-drag').attr('stroke-width', 1);
		const newLabel = (selection.select('input').node() as HTMLInputElement).value;
		selection.datum().label = newLabel;
		selection.select('text').text(newLabel).style('fill-opacity', 1.0);
		selection.select('foreignObject').remove();
	}

	selectEdge(selection: D3SelectionIEdge<EdgeData>) {
		selection.selectAll('path').style('stroke-width', 3);
	}

	deselectEdge(selection: D3SelectionIEdge<EdgeData>) {
		selection.selectAll('path').style('stroke-width', 2);
	}

	toggoleNodeSelectionByLabel(label: string) {
		const selection = this.chart?.selectAll('.node-ui').filter((d: any) => d.label === label);
		if (selection && selection.size() === 1) {
			this.toggleNodeSelection(selection as D3SelectionINode<NodeData>);
		}
	}

	toggleNodeSelection(selection: D3SelectionINode<NodeData>) {
		if (this.nodeSelection && this.nodeSelection.datum().id === selection.datum().id) {
			this?.chart?.selectAll('.node-ui').style('opacity', 1);
			this?.chart?.selectAll('.edge').style('opacity', 1);
			this.deselectNode(this.nodeSelection);
			this.nodeSelection = null;
		} else {
			if (this.nodeSelection) {
				this.deselectNode(this.nodeSelection);
				this.nodeSelection.selectAll('.no-drag').style('opacity', 0).style('visibility', 'hidden');
			}

			// Set focus on node:
			this?.chart?.selectAll('.node-ui').style('opacity', 0.3);
			this?.chart?.selectAll('.edge').style('opacity', 0.3);
			selection.style('opacity', 1);
			this.nodeSelection = selection;

			if (this.editMode) {
				selection
					.selectAll('.no-drag')
					.transition('ease-out')
					.duration(200)
					.style('opacity', 1)
					.style('visibility', 'visible');
				this.selectNodeEdit(this.nodeSelection);
			}
		}

		if (this.edgeSelection) {
			this.deselectEdge(this.edgeSelection);
			this.edgeSelection = null;
		}
	}

	postRenderProcess() {
		const chart = this.chart;
		const svg = d3.select(this.svgEl);
		const start: { x: number; y: number } = { x: 0, y: 0 };
		const end: { x: number; y: number } = { x: 0, y: 0 };

		let sourceData: INode<NodeData> | null = null;
		let targetData: INode<NodeData> | null = null;

		// Reset all
		this.removeAllEvents('node-drag-start');
		this.removeAllEvents('node-drag-move');
		this.removeAllEvents('node-drag-end');

		// (Re)create dragging listeners
		this.on('node-drag-start', (_eventName, event, selection: D3SelectionINode<NodeData>) => {
			// set colour on drag
			selection.selectAll('.selectableNode').attr('stroke', HIGHLIGHTEDSTROKECOLOUR);

			if (!this.isDragEnabled) return;
			sourceData = selection.datum();
			start.x = sourceData.x;
			start.y = sourceData.y;

			const targetSelection = d3.select(event.sourceEvent.target);
			start.x += +targetSelection.attr('cx');
			start.y += +targetSelection.attr('cy');
		});

		this.on(
			'node-drag-move',
			(_eventName, event /* , _selection: D3SelectionINode<NodeData> */) => {
				this.updateMultiEdgeLabels();
				if (!this.isDragEnabled) return;
				const pointerCoords = d3
					.zoomTransform(svg.node() as Element)
					.invert(d3.pointer(event, svg.node()));
				targetData = d3.select<SVGGElement, INode<NodeData>>(event.sourceEvent.target).datum();
				if (targetData) {
					end.x = targetData.x;
					end.y = targetData.y;
				} else {
					end.x = pointerCoords[0];
					end.y = pointerCoords[1];
				}
				chart?.selectAll('.new-edge').remove();

				const line = [
					{ x: start.x, y: start.y },
					{ x: end.x, y: end.y }
				];
				chart
					?.append('path')
					.classed('new-edge', true)
					.attr('d', pathFn(line))
					.attr('marker-end', 'url(#arrowhead)')
					.style('stroke-width', 3)
					.style('stroke', 'var(--primary-color)');
			}
		);

		this.on('node-drag-end', (_eventName, _event, selection: D3SelectionINode<NodeData>) => {
			chart?.selectAll('.new-edge').remove();
			// reset colour after drag
			selection.selectAll('.selectableNode').attr('stroke', 'var(--petri-nodeBorder)');

			if (!this.isDragEnabled) return;
			if (targetData && sourceData) {
				this.emit('add-edge', null, null, { target: targetData, source: sourceData });
				sourceData = null;
				targetData = null;
			}
		});

		this.on('node-click', (_eventName, _event, selection: D3SelectionINode<NodeData>) => {
			this.toggleNodeSelection(selection);
		});

		this.on('edge-click', (_eventName, _event, selection: D3SelectionIEdge<EdgeData>) => {
			if (!this.editMode) return;

			if (this.edgeSelection) {
				this.deselectEdge(this.edgeSelection);
			}
			if (this.nodeSelection) {
				this.deselectNode(this.nodeSelection);
				this.nodeSelection.selectAll('.no-drag').style('opacity', 0).style('visibility', 'hidden');
				this.nodeSelection = null;
			}

			this.edgeSelection = selection;
			this.selectEdge(this.edgeSelection);
		});

		this.on('background-click', () => {
			// Reset opacity from focus:
			this?.chart?.selectAll('.node-ui').style('opacity', 1);
			this?.chart?.selectAll('.edge').style('opacity', 1);

			if (this.edgeSelection) {
				this.deselectEdge(this.edgeSelection);
				this.edgeSelection = null;
			}
			if (this.nodeSelection) {
				this.deselectNode(this.nodeSelection);
				this.nodeSelection.selectAll('.no-drag').style('opacity', 0).style('visibility', 'hidden');
				this.nodeSelection = null;
			}
		});

		// restore prior selection state, if applicable
		if (this.nodeSelection && this.chart) {
			const id = this.nodeSelection.datum().id;
			this.nodeSelection = this.chart
				.selectAll('.node-ui')
				.filter((d: any) => d.id === id) as D3SelectionINode<NodeData>;

			if (this.nodeSelection) {
				this.selectNodeEdit(this.nodeSelection);
			}
		}
	}

	getShapeOffset(node: any, angle: number) {
		switch (node.data.type) {
			case NodeType.Transition: {
				// transitions -> squares
				return { x: node.x, y: node.y };
			}
			case NodeType.State: {
				// species -> circles with multiplier
				const radius = node.width * 1.5;
				return { x: node.x + radius * Math.cos(angle), y: node.y + radius * Math.sin(angle) };
			}
			default:
				return { x: node.x, y: node.y };
		}
	}

	addNode(type: string, name: string, pos: { x: number; y: number }) {
		// FIXME: hardwired sizing
		const size = type === NodeType.State ? 60 : 30;
		const id = `${type}-${this.graph.nodes.length + 1}`;
		this.graph.nodes.push({
			id,
			label: name,
			x: pos.x,
			y: pos.y,
			width: size,
			height: size,
			nodes: [],
			data: {
				type
			}
		});

		const amr = this.graph.amr as Model;
		if (type === NodeType.State) {
			petrinetService.addState(amr, id, name);
		} else {
			petrinetService.addTransition(amr, id, name);
		}
		this.render();
	}

	addNodeCenter(type: string, name: string) {
		// FIXME: hardwired sizing
		const positionX = this.chartSize.width / 2;
		const positionY = this.chartSize.height / 2;
		this.addNode(type, name, { x: positionX, y: positionY });
	}

	removeNode(id: string) {
		const nodeData = this.nodeSelection?.datum();
		if (!nodeData) return;
		_.remove(this.graph.edges, (e) => e.source === nodeData.id || e.target === nodeData.id);
		_.remove(this.graph.nodes, (n) => n.id === nodeData.id);
		this.nodeSelection = null;
		this.render();

		const amr = this.graph.amr as Model;
		if (nodeData.data.type === NodeType.State) {
			petrinetService.removeState(amr, id);
		} else {
			petrinetService.removeTransition(amr, id);
		}
	}

	addEdge(source: any, target: any) {
		// prevent nodes with same type from being linked with each other
		if (source.data.type === target.data.type) {
			return;
		}

		const existingEdge = this.graph.edges.find(
			(edge) => edge.source === source.id && edge.target === target.id
		);
		if (existingEdge && existingEdge.data) {
			existingEdge.data.numEdges++;
		} else {
			this.graph.edges.push({
				id: `${source.id}_${target.id}`,
				source: source.id,
				target: target.id,
				points: [
					{ x: source.x, y: source.y },
					{ x: target.x, y: target.y }
				],
				data: { numEdges: 1 }
			});
		}
		this.render();

		const amr = this.graph.amr as Model;
		petrinetService.addEdge(amr, source.id, target.id);
	}

	removeEdge(sourceId: string, targetId: string) {
		const existingEdge = this.graph.edges.find(
			(edge) => edge.source === sourceId && edge.target === targetId
		);
		if (existingEdge && existingEdge.data) {
			existingEdge.data.numEdges--;
		} else {
			this.graph.edges = this.graph.edges.filter(
				(d) => d.source === sourceId && d.target === targetId
			);
		}
		this.render();

		const amr = this.graph.amr as Model;
		petrinetService.removeEdge(amr, sourceId, targetId);
	}
}
