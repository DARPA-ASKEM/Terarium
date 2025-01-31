package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.InputPort;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.OutputPort;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.Workflow;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowAnnotation;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowEdge;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowNode;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowPositions;
import software.uncharted.terarium.hmiserver.repository.data.WorkflowRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
@Slf4j
public class WorkflowService extends TerariumAssetService<Workflow, WorkflowRepository> {

	public WorkflowService(
		final ObjectMapper objectMapper,
		final Config config,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final S3ClientService s3ClientService,
		final WorkflowRepository repository
	) {
		super(objectMapper, config, projectService, projectAssetService, repository, s3ClientService, Workflow.class);
	}

	@Observed(name = "function_profile")
	public Set<Workflow> findWorkflowsToClean() {
		final Set<Workflow> workflows = new HashSet<>();
		workflows.addAll(repository.findWorkflowsWithEdgesToBeDeleted());
		workflows.addAll(repository.findWorkflowsWithNodesToBeDeleted());
		return workflows;
	}

	@Observed(name = "function_profile")
	public Optional<List<Workflow>> updateWorkflows(final Collection<Workflow> assets)
		throws IOException, IllegalArgumentException {
		final List<Workflow> workflows = repository.saveAll(assets);
		return Optional.of(workflows);
	}

	@Override
	@Observed(name = "function_profile")
	public Workflow createAsset(final Workflow asset, final UUID projectId, final Schema.Permission hasWritePermission)
		throws IOException, IllegalArgumentException {
		// ensure the workflow id is set correctly
		if (asset.getNodes() != null) {
			for (final WorkflowNode node : asset.getNodes()) {
				node.setWorkflowId(asset.getId());
				if (node.getVersion() == null) {
					node.setVersion(1L);
				}
				if (node.getInputs() != null) {
					for (final InputPort port : node.getInputs()) {
						if (port.getVersion() == null) {
							port.setVersion(1L);
						}
					}
				}
				if (node.getOutputs() != null) {
					for (final OutputPort port : node.getOutputs()) {
						if (port.getVersion() == null) {
							port.setVersion(1L);
						}
					}
				}
			}
		}
		if (asset.getEdges() != null) {
			for (final WorkflowEdge edge : asset.getEdges()) {
				edge.setWorkflowId(asset.getId());
				if (edge.getVersion() == null) {
					edge.setVersion(1L);
				}
			}
		}
		return super.createAsset(asset, projectId, hasWritePermission);
	}

	@Override
	@Observed(name = "function_profile")
	public Optional<Workflow> updateAsset(
		final Workflow asset,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException, IllegalArgumentException {
		// Fetch database copy, we will update into it
		final Workflow dbWorkflow = getAsset(asset.getId(), hasWritePermission).get();

		List<WorkflowNode> dbWorkflowNodes = dbWorkflow.getNodes();
		List<WorkflowEdge> dbWorkflowEdges = dbWorkflow.getEdges();
		final Map<UUID, WorkflowNode> nodeMap = new HashMap<>();
		final Map<UUID, WorkflowEdge> edgeMap = new HashMap<>();

		dbWorkflow.setName(asset.getName());
		dbWorkflow.setDescription(asset.getDescription());
		dbWorkflow.setScenario(asset.getScenario());
		dbWorkflow.setAnnotations(asset.getAnnotations());

		// Prep: sane state, cache the nodes/edges to update for easy retrival
		if (asset.getNodes() != null) {
			for (final WorkflowNode node : asset.getNodes()) {
				node.setWorkflowId(asset.getId());
				if (node.getVersion() == null) {
					node.setVersion(1L);
				}
				if (node.getInputs() != null) {
					for (final InputPort port : node.getInputs()) {
						if (port.getVersion() == null) {
							port.setVersion(1L);
						}
					}
				}
				if (node.getOutputs() != null) {
					for (final OutputPort port : node.getOutputs()) {
						if (port.getVersion() == null) {
							port.setVersion(1L);
						}
					}
				}
				nodeMap.put(node.getId(), node);
			}
		}
		if (asset.getEdges() != null) {
			for (final WorkflowEdge edge : asset.getEdges()) {
				edge.setWorkflowId(asset.getId());
				if (edge.getVersion() == null) {
					edge.setVersion(1L);
				}
				edgeMap.put(edge.getId(), edge);
			}
		}

		////////////////////////////////////////////////////////////////////////////////
		// Handle updates and deletes (tombdstoning)
		////////////////////////////////////////////////////////////////////////////////
		if (dbWorkflowNodes != null && dbWorkflowNodes.size() > 0) {
			for (int index = 0; index < dbWorkflowNodes.size(); index++) {
				final WorkflowNode dbNode = dbWorkflowNodes.get(index);
				final WorkflowNode node = nodeMap.get(dbNode.getId());

				if (node == null) continue;

				final JsonNode nodeContent = this.objectMapper.valueToTree(node);
				final JsonNode dbNodeContent = this.objectMapper.valueToTree(dbNode);

				// Remove inputs/outputs, these are dealt with separately
				((ObjectNode) nodeContent).remove("inputs");
				((ObjectNode) nodeContent).remove("outputs");
				((ObjectNode) dbNodeContent).remove("inputs");
				((ObjectNode) dbNodeContent).remove("outputs");

				// No changes, skip
				boolean sameContent = false;
				if (nodeContent.equals(dbNodeContent)) {
					sameContent = true;
				}

				// Only update if if node is not already deleted in the db
				if (sameContent == false) {
					if (dbNode.getIsDeleted() == false && dbNode.getVersion().equals(node.getVersion())) {
						dbNode.setVersion(dbNode.getVersion() + 1L);
						dbNode.setCreatedBy(node.getCreatedBy());
						dbNode.setCreatedAt(node.getCreatedAt());
						dbNode.setDisplayName(node.getDisplayName());
						dbNode.setOperationType(node.getOperationType());
						dbNode.setDocumentationUrl(node.getDocumentationUrl());
						dbNode.setImageUrl(node.getImageUrl());
						dbNode.setUniqueInputs(node.getUniqueInputs());
						dbNode.setIsDeleted(node.getIsDeleted());

						dbNode.setX(node.getX());
						dbNode.setY(node.getY());
						dbNode.setWidth(node.getWidth());
						dbNode.setHeight(node.getHeight());

						dbNode.setStatus(node.getStatus());
						dbNode.setState(node.getState());
						dbNode.setActive(node.getActive());
					} else {
						log.warn("Node version conflict node id=" + dbNode.getId() + ", workflow id=" + dbWorkflow.getId());
					}
				}

				// Manage outputs
				if (dbNode.getIsDeleted() == false && node.getOutputs() != null && node.getOutputs().size() > 0) {
					for (final OutputPort port : node.getOutputs()) {
						final OutputPort dbPort = dbNode
							.getOutputs()
							.stream()
							.filter(p -> p.getId().equals(port.getId()))
							.findFirst()
							.orElse(null);
						if (dbPort == null) {
							dbNode.getOutputs().add(port);
						} else {
							final JsonNode portContent = this.objectMapper.valueToTree(port);
							final JsonNode dbPortContent = this.objectMapper.valueToTree(dbPort);
							if (portContent.equals(dbPortContent)) {
								continue; // Nothing to update
							}

							// Make old workflow compatible
							if (dbPort.getVersion() == null) {
								dbPort.setVersion(1L);
							}

							if (dbPort.getVersion().equals(port.getVersion())) {
								dbPort.setVersion(dbPort.getVersion() + 1L);
								dbPort.setType(port.getType());
								dbPort.setOriginalType(port.getOriginalType());
								dbPort.setState(port.getState());
								dbPort.setStatus(port.getStatus());
								dbPort.setValue(port.getValue());
								dbPort.setIsSelected(port.getIsSelected());
								dbPort.setLabel(port.getLabel());
								dbPort.setTimestamp(port.getTimestamp());
								dbPort.setOperatorStatus(port.getOperatorStatus());
							} else {
								log.warn(
									"Port version conflict port id=" +
									dbPort.getId() +
									", node id=" +
									dbNode.getId() +
									", workflow id=" +
									dbWorkflow.getId()
								);
							}
						}
					}

					// Normalize
					final List<OutputPort> filteredOutputs = dbNode
						.getOutputs()
						.stream()
						.filter(output -> output.getValue() != null)
						.collect(Collectors.toList());
					if (filteredOutputs.size() > 0) {
						dbNode.setOutputs(filteredOutputs);
					}
				}

				// Manage inputs
				if (dbNode.getIsDeleted() == false && node.getInputs() != null && node.getInputs().size() > 0) {
					for (final InputPort port : node.getInputs()) {
						final InputPort dbPort = dbNode
							.getInputs()
							.stream()
							.filter(p -> p.getId().equals(port.getId()))
							.findFirst()
							.orElse(null);
						if (dbPort == null) {
							dbNode.getInputs().add(port);
						} else {
							final JsonNode portContent = this.objectMapper.valueToTree(port);
							final JsonNode dbPortContent = this.objectMapper.valueToTree(dbPort);
							if (portContent.equals(dbPortContent)) {
								continue; // Nothing to update
							}

							// Make old workflow compatible
							if (dbPort.getVersion() == null) {
								dbPort.setVersion(1L);
							}

							if (dbPort.getVersion().equals(port.getVersion())) {
								dbPort.setVersion(dbPort.getVersion() + 1L);
								dbPort.setType(port.getType());
								dbPort.setOriginalType(port.getOriginalType());
								dbPort.setStatus(port.getStatus());
								dbPort.setValue(port.getValue());
								dbPort.setLabel(port.getLabel());
							} else {
								log.warn(
									"Port version conflict port id=" +
									dbPort.getId() +
									", node id=" +
									dbNode.getId() +
									", workflow id=" +
									dbWorkflow.getId()
								);
							}
						}
					}
				}

				// remove once we processed the update
				nodeMap.remove(node.getId());
			}
		}

		if (dbWorkflowEdges != null && dbWorkflowEdges.size() > 0) {
			for (int index = 0; index < dbWorkflowEdges.size(); index++) {
				final WorkflowEdge dbEdge = dbWorkflowEdges.get(index);
				final WorkflowEdge edge = edgeMap.get(dbEdge.getId());

				if (edge == null) continue;

				final JsonNode edgeContent = this.objectMapper.valueToTree(edge);
				final JsonNode dbEdgeContent = this.objectMapper.valueToTree(dbEdge);

				// No changes, skip
				if (edgeContent.equals(dbEdgeContent)) {
					edgeMap.remove(edge.getId());
					continue;
				}

				// Only update if if edge is not already deleted in the db
				if (dbEdge.getIsDeleted() == false && dbEdge.getVersion().equals(edge.getVersion())) {
					edge.setVersion(dbEdge.getVersion() + 1L);
					dbWorkflowEdges.set(index, edge);
				}

				// remove once updated
				edgeMap.remove(edge.getId());
			}
		}

		////////////////////////////////////////////////////////////////////////////////
		// Handle new nodes or edges
		////////////////////////////////////////////////////////////////////////////////
		if (dbWorkflowNodes == null) {
			dbWorkflowNodes = new ArrayList<>();
		}
		for (final Map.Entry<UUID, WorkflowNode> pair : nodeMap.entrySet()) {
			dbWorkflowNodes.add(pair.getValue());
		}

		if (dbWorkflowEdges == null) {
			dbWorkflowEdges = new ArrayList<>();
		}
		for (final Map.Entry<UUID, WorkflowEdge> pair : edgeMap.entrySet()) {
			dbWorkflowEdges.add(pair.getValue());
		}

		final Optional<Workflow> result = super.updateAsset(dbWorkflow, projectId, hasWritePermission);
		return result;
	}

	@Observed(name = "function_profile")
	private void cascadeInvalidStatus(final WorkflowNode sourceNode, final Map<UUID, List<WorkflowNode>> nodeCache) {
		sourceNode.setStatus("invalid");
		final List<WorkflowNode> downstreamNodes = nodeCache.get(sourceNode.getId());
		if (downstreamNodes == null) return;

		for (final WorkflowNode node : downstreamNodes) {
			cascadeInvalidStatus(node, nodeCache);
		}
	}

	public void addNode(final Workflow workflow, final WorkflowNode node) {
		workflow.getNodes().add(node);
	}

	public void removeNodes(final Workflow workflow, final List<UUID> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			removeNode(workflow, nodes.get(i));
		}
	}

	public void removeNode(final Workflow workflow, final UUID nodeId) {
		final WorkflowNode nodeToRemove = getOperator(workflow, nodeId);
		if (nodeToRemove == null) return;

		// Remove all the connecting edges first
		final List<WorkflowEdge> edgesToRemove = workflow
			.getEdges()
			.stream()
			.filter(edge -> edge.getSource().equals(nodeToRemove.getId()) || edge.getTarget().equals(nodeToRemove.getId()))
			.collect(Collectors.toList());

		for (final WorkflowEdge edge : edgesToRemove) {
			this.removeEdge(workflow, edge.getId());
		}
		nodeToRemove.setIsDeleted(true);
	}

	/**
	 * Create an edge between two nodes, and transfer the value from
	 * source-node's output port to target-node's input port
	 *
	 * The terms are used as depicted in the following schematics
	 *
	 *
	 *  ------------             ------------
	 *  | Source    |            | Target    |
	 *  |           |            |           |
	 *  |  [output port] ---> [input port]   |
	 *  |           |            |           |
	 *  ------------             -------------
	 *
	 *
	 * There are several special cases
	 *
	 * The target-input can accept multiple types, but this is more of an exception
	 * rather than the norm. This is resolved by checking one of the accepted type
	 * matches the provided type.
	 *
	 * The second case is when the source produce multiple types, a similar check, but
	 * on the reverse side is performed.
	 *
	 *
	 * Ambiguity arises when the source provide multiple types and the target accepts multiple
	 * types and there is no resolution. For example
	 * - source provides {A, B, C}
	 * - target accepts A or C
	 * We do not deal with this and throw an error/warning, and the edge creation will be cancelled.
	 *
	 * */
	public void addEdge(final Workflow workflow, final WorkflowEdge edge) {
		if (edge.getId() == null) {
			edge.setId(UUID.randomUUID());
		}

		final WorkflowEdge existingEdge = workflow
			.getEdges()
			.stream()
			.filter(e -> {
				return (
					e.getIsDeleted() == false &&
					e.getSource().equals(edge.getSource()) &&
					e.getSourcePortId().equals(edge.getSourcePortId()) &&
					e.getTarget().equals(edge.getTarget()) &&
					e.getTargetPortId().equals(edge.getTargetPortId())
				);
			})
			.findFirst()
			.orElse(null);

		if (existingEdge != null) return;

		// Check if connection itself compatible
		final WorkflowNode sourceNode = workflow
			.getNodes()
			.stream()
			.filter(node -> node.getId().equals(edge.getSource()))
			.findFirst()
			.orElse(null);
		final WorkflowNode targetNode = workflow
			.getNodes()
			.stream()
			.filter(node -> node.getId().equals(edge.getTarget()))
			.findFirst()
			.orElse(null);

		if (sourceNode == null || targetNode == null) return;

		final OutputPort sourceOutputPort = sourceNode
			.getOutputs()
			.stream()
			.filter(port -> port.getId().equals(edge.getSourcePortId()))
			.findFirst()
			.orElse(null);
		final InputPort targetInputPort = targetNode
			.getInputs()
			.stream()
			.filter(port -> port.getId().equals(edge.getTargetPortId()))
			.findFirst()
			.orElse(null);
		if (sourceOutputPort == null || targetInputPort == null) return;

		// Check if connection data type is compatible
		final List<String> outputTypes = splitAndTrim(sourceOutputPort.getType());
		final List<String> allowedInputTypes = splitAndTrim(targetInputPort.getType());
		final List<String> intersectionTypes = findIntersection(outputTypes, allowedInputTypes);

		// Not supported if there are more than one match
		if (intersectionTypes.size() > 1) {
			log.warn("Ambiguous matching types " + outputTypes + " " + allowedInputTypes);
		}

		// Not supported if there is a mismatch
		if (intersectionTypes.size() == 0 || targetInputPort.getStatus().equals("connected")) {
			return;
		}

		// Uniquness check, for example target-node accepts list of model-configs, but the
		// model-configs must be unique, so if we connect config-1, we cannot connect connect-1
		// again if if it from a different node
		if (targetNode.getUniqueInputs() == true) {
			final List<InputPort> existingPorts = targetNode
				.getInputs()
				.stream()
				.filter(port -> {
					final ArrayNode targetPortValue = port.getValue();
					final ArrayNode sourcePortValue = sourceOutputPort.getValue();
					if (targetPortValue != null && sourcePortValue != null) {
						if (targetPortValue.get(0).equals(sourcePortValue.get(0))) {
							return true;
						}
					}
					return false;
				})
				.collect(Collectors.toList());
			if (existingPorts.size() > 0) {
				return;
			}
		}

		// Transfer data value/reference
		targetInputPort.setLabel(sourceOutputPort.getLabel());
		if (outputTypes.size() > 1) {
			final String concreteType = intersectionTypes.get(0);
			if (sourceOutputPort.getValue() != null) {
				final ArrayNode arrayNode = objectMapper.createArrayNode();
				arrayNode.add(sourceOutputPort.getValue().get(0).get(concreteType));
				targetInputPort.setValue(arrayNode);
			}
		} else {
			targetInputPort.setValue(sourceOutputPort.getValue());
		}

		// Transfer concrete type to the input type to match the output type
		// Saves the original type in case we want to revert when we unlink the edge
		// FIXME: check if the logic is correct when both output/input are multi types
		if (allowedInputTypes.size() > 1) {
			targetInputPort.setOriginalType(targetInputPort.getType());
			targetInputPort.setType(sourceOutputPort.getType());
		}

		// Set connection status
		targetInputPort.setStatus("connected");
		sourceOutputPort.setStatus("connected");

		// Finall add to workflow
		workflow.getEdges().add(edge);
	}

	public void removeEdges(final Workflow workflow, final List<UUID> edges) {
		for (int i = 0; i < edges.size(); i++) {
			removeEdge(workflow, edges.get(i));
		}
	}

	public void removeEdge(final Workflow workflow, final UUID edgeId) {
		final WorkflowEdge edgeToRemove = workflow
			.getEdges()
			.stream()
			.filter(edge -> edge.getId().equals(edgeId))
			.findFirst()
			.orElse(null);

		if (edgeToRemove == null || edgeToRemove.getIsDeleted() == true) return;

		// Remove the data reference at the targetPort
		final WorkflowNode targetNode = workflow
			.getNodes()
			.stream()
			.filter(node -> node.getId().equals(edgeToRemove.getTarget()))
			.findFirst()
			.orElse(null);
		if (targetNode == null) return;

		final InputPort targetPort = targetNode
			.getInputs()
			.stream()
			.filter(port -> port.getId().equals(edgeToRemove.getTargetPortId()))
			.findFirst()
			.orElse(null);
		if (targetPort == null) return;

		final WorkflowNode sourceNode = workflow
			.getNodes()
			.stream()
			.filter(node -> node.getId().equals(edgeToRemove.getSource()))
			.findFirst()
			.orElse(null);
		if (sourceNode == null) return;

		final OutputPort sourcePort = sourceNode
			.getOutputs()
			.stream()
			.filter(port -> port.getId().equals(edgeToRemove.getSourcePortId()))
			.findFirst()
			.orElse(null);
		if (sourcePort == null) return;

		// Disconnect logic
		targetPort.setValue(null);
		targetPort.setStatus("not connected");
		targetPort.setLabel(null);

		if (targetPort.getOriginalType() != null) {
			targetPort.setType(targetPort.getOriginalType());
		}

		// Invalidate downstream
		final Map<UUID, WorkflowNode> nodeMap = buildNodeMap(workflow);
		final Map<UUID, List<WorkflowNode>> nodeCache = new HashMap<>();

		for (final WorkflowEdge edge : workflow.getEdges()) {
			if (edge.getIsDeleted() == true) {
				continue;
			}
			if (!nodeCache.containsKey(edge.getSource())) {
				nodeCache.put(edge.getSource(), new ArrayList());
			}
			nodeCache.get(edge.getSource()).add(nodeMap.get(edge.getTarget()));
		}
		cascadeInvalidStatus(targetNode, nodeCache);

		// Remove
		edgeToRemove.setIsDeleted(true);

		// If there are no more references reset the connected status of the source node
		final List<WorkflowEdge> remainingEdges = workflow
			.getEdges()
			.stream()
			.filter(edge -> edge.getIsDeleted() == false && edge.getSource().equals(edgeToRemove.getSource()))
			.collect(Collectors.toList());

		if (remainingEdges.size() == 0) {
			sourcePort.setStatus("not connected");
		}
	}

	/**
	 * Updates the operator's state using the data from a specified WorkflowOutput. If the operator's
	 * current state was not previously stored as a WorkflowOutput, this function first saves the current state
	 * as a new WorkflowOutput. It then replaces the operator's existing state with the data from the specified WorkflowOutput.
	 *
	 **/
	@Observed(name = "function_profile")
	public void selectOutput(final Workflow workflow, final UUID nodeId, final UUID selectedId) throws Exception {
		final WorkflowNode operator = getOperator(workflow, nodeId);
		if (operator == null) {
			throw new Exception("Cannot find node " + nodeId);
		}

		for (final OutputPort port : operator.getOutputs()) {
			port.setIsSelected(false);
			port.setStatus("not connected");
		}

		// Update the Operator state with the selected one
		final OutputPort selected = operator
			.getOutputs()
			.stream()
			.filter(port -> port.getId().equals(selectedId))
			.findAny()
			.orElse(null);

		if (selected == null) {
			throw new Exception("Cannot find port " + selectedId);
		}

		selected.setIsSelected(true);
		if (selected.getState() != null) {
			operator.setState(deepMergeWithOverwrite(operator.getState(), selected.getState()));
		}
		operator.setStatus(selected.getOperatorStatus());
		operator.setActive(selected.getId());

		// If this output is connected to input port(s), update the input port(s), otherwise return
		final List<WorkflowEdge> outgoingEdges = workflow
			.getEdges()
			.stream()
			.filter(edge -> edge.getSource().equals(operator.getId()) && edge.getIsDeleted() == false)
			.collect(Collectors.toList());
		if (outgoingEdges.size() == 0) return;

		selected.setStatus("connected");

		final Map<UUID, WorkflowNode> nodeMap = new HashMap<>();
		final Map<UUID, List<WorkflowNode>> nodeCache = new HashMap<>();
		for (final WorkflowNode node : workflow.getNodes()) {
			if (node.getIsDeleted() == false) {
				nodeMap.put(node.getId(), node);
			}
		}
		nodeCache.put(selected.getId(), new ArrayList());

		for (final WorkflowEdge edge : workflow.getEdges()) {
			if (edge.getIsDeleted() == true) continue;

			if (edge.getSource().equals(operator.getId())) {
				final WorkflowNode targetNode = workflow
					.getNodes()
					.stream()
					.filter(node -> node.getId().equals(edge.getTarget()))
					.findAny()
					.orElse(null);
				if (targetNode == null) continue;

				// Update the input port of the target node
				final InputPort targetPort = targetNode
					.getInputs()
					.stream()
					.filter(port -> port.getId().equals(edge.getTargetPortId()))
					.findAny()
					.orElse(null);
				if (targetPort == null) continue;

				// Sync edge source port to selected output
				edge.setSourcePortId(selected.getId());

				//
				// Handle compound type unwrangling, this is very similar to what
				// we do in addEdge(...) but s already connected.
				///
				final List<String> selectedTypes = splitAndTrim(selected.getType());
				final List<String> allowedInputTypes = splitAndTrim(targetPort.getType());
				final List<String> intersectionTypes = findIntersection(selectedTypes, allowedInputTypes);

				// Sanity check: multiple matches found
				if (intersectionTypes.size() > 1) {
					log.warn("Ambiguous matching types " + selectedTypes + " " + allowedInputTypes);
					continue;
				}

				// Sanity check: no matches found
				if (intersectionTypes.size() == 0) {
					log.warn("No matching types " + selectedTypes + " " + allowedInputTypes);
					continue;
				}

				// Finally we should be okay at this point
				if (selectedTypes.size() > 1) {
					final String concreteType = intersectionTypes.get(0);
					if (selected.getValue() != null) {
						final ArrayNode arrayNode = objectMapper.createArrayNode();
						arrayNode.add(selected.getValue().get(0).get(concreteType));
						targetPort.setValue(arrayNode);
					}
				} else {
					targetPort.setValue(selected.getValue());
				}
				targetPort.setLabel(selected.getLabel());
			}

			// Collect node cache
			if (edge.getSource() == null || edge.getTarget() == null) continue;
			if (!nodeCache.containsKey(edge.getSource())) {
				nodeCache.put(edge.getSource(), new ArrayList());
			}
			nodeCache.get(edge.getSource()).add(nodeMap.get(edge.getTarget()));
		}

		final List<WorkflowNode> downstreamNodes = nodeCache.get(operator.getId());
		for (final WorkflowNode downstreamNode : downstreamNodes) {
			cascadeInvalidStatus(downstreamNode, nodeCache);
		}
	}

	@Observed(name = "function_profile")
	public void appendInput(final Workflow workflow, final UUID nodeId, final InputPort port) throws Exception {
		final WorkflowNode operator = getOperator(workflow, nodeId);
		if (operator == null) {
			throw new Exception("Cannot find node " + nodeId);
		}
		operator.getInputs().add(port);
	}

	@Observed(name = "function_profile")
	public void appendOutput(final Workflow workflow, final UUID nodeId, final OutputPort port, final JsonNode nodeState)
		throws Exception {
		final WorkflowNode operator = getOperator(workflow, nodeId);
		if (operator == null) {
			throw new Exception("Cannot find node " + nodeId);
		}

		// If nodeState is provided, also set the state
		if (nodeState != null && nodeState.isNull() == false) {
			operator.setState(nodeState);
		}

		// We assume that if we can produce an output, the status is okay
		operator.setStatus("success");
		operator.getOutputs().add(port);
		operator.setActive(port.getId());

		// TODO: may need to check race conditions
		operator.setOutputs(
			operator.getOutputs().stream().filter(output -> output.getValue() != null).collect(Collectors.toList())
		);

		selectOutput(workflow, nodeId, port.getId());
	}

	public void updateNodeState(final Workflow workflow, final Map<UUID, JsonNode> stateMap) {
		final Map<UUID, WorkflowNode> nodeMap = buildNodeMap(workflow);
		for (final Map.Entry<UUID, JsonNode> entry : stateMap.entrySet()) {
			final WorkflowNode node = nodeMap.get(entry.getKey());
			if (node == null) {
				log.warn("Node not found " + entry.getKey());
			}
			if (node == null || node.getIsDeleted() == true) continue;

			node.setState(entry.getValue());
		}
	}

	public void updateNodeStatus(final Workflow workflow, final Map<UUID, String> statusMap) {
		final Map<UUID, WorkflowNode> nodeMap = buildNodeMap(workflow);
		for (final Map.Entry<UUID, String> entry : statusMap.entrySet()) {
			final WorkflowNode node = nodeMap.get(entry.getKey());
			if (node == null) {
				log.warn("Node not found " + entry.getKey());
			}
			if (node == null || node.getIsDeleted() == true) continue;

			node.setStatus(entry.getValue());
		}
	}

	public void updatePositions(final Workflow workflow, final WorkflowPositions positions) {
		for (final WorkflowNode node : workflow.getNodes()) {
			final WorkflowPositions.Position pos = positions.getNodes().get(node.getId());
			if (pos == null) continue;
			node.setX(pos.getX());
			node.setY(pos.getY());
		}

		for (final WorkflowEdge edge : workflow.getEdges()) {
			final List<WorkflowPositions.Position> posList = positions.getEdges().get(edge.getId());
			if (posList == null) continue;
			edge.getPoints().set(0, this.objectMapper.valueToTree(posList.get(0)));
			edge.getPoints().set(1, this.objectMapper.valueToTree(posList.get(1)));
		}
	}

	public void addOrUpdateAnnotation(final Workflow workflow, final WorkflowAnnotation annotation) {
		if (workflow.getAnnotations() == null || workflow.getAnnotations().isEmpty()) {
			workflow.setAnnotations(new HashMap<UUID, WorkflowAnnotation>());
		}
		if (annotation.getId() == null) {
			annotation.setId(UUID.randomUUID());
		}
		workflow.getAnnotations().put(annotation.getId(), annotation);
	}

	public void removeAnnotation(final Workflow workflow, final UUID annotationId) {
		if (workflow.getAnnotations() == null) return;
		workflow.getAnnotations().remove(annotationId);
	}

	@Override
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Workflows are not stored in S3");
	}

	////////////////////////////////////////////////////////////////////////////////
	// Helpers
	////////////////////////////////////////////////////////////////////////////////
	private WorkflowNode getOperator(final Workflow workflow, final UUID nodeId) {
		final WorkflowNode operator = workflow
			.getNodes()
			.stream()
			.filter(node -> {
				return node.getIsDeleted() == false && node.getId().equals(nodeId);
			})
			.findFirst()
			.orElse(null);
		return operator;
	}

	// Build a lookup map for faster node retrival
	private Map<UUID, WorkflowNode> buildNodeMap(final Workflow workflow) {
		final Map<UUID, WorkflowNode> map = new HashMap<>();
		for (final WorkflowNode node : workflow.getNodes()) {
			if (node.getIsDeleted() == false) {
				map.put(node.getId(), node);
			}
		}
		return map;
	}

	// Build a lookup map for faster edge retrival
	private Map<UUID, WorkflowEdge> buildEdgeMap(final Workflow workflow) {
		final Map<UUID, WorkflowEdge> map = new HashMap<>();
		for (final WorkflowEdge edge : workflow.getEdges()) {
			if (edge.getIsDeleted() == false) {
				map.put(edge.getId(), edge);
			}
		}
		return map;
	}

	// Merge nodeB into nodeA
	public static JsonNode deepMergeWithOverwrite(final JsonNode nodeA, final JsonNode nodeB) {
		if (nodeA == null && nodeB == null) {
			return null;
		} else if (nodeA == null && nodeB != null) {
			return nodeB;
		}

		if (nodeA.isObject() && nodeB.isObject()) {
			final ObjectNode objectA = (ObjectNode) nodeA;
			nodeB
				.fields()
				.forEachRemaining(entry -> {
					final String fieldName = entry.getKey();
					final JsonNode valueB = entry.getValue();
					if (objectA.has(fieldName)) {
						final JsonNode valueA = objectA.get(fieldName);
						objectA.set(fieldName, deepMergeWithOverwrite(valueA, valueB));
					} else {
						objectA.set(fieldName, valueB);
					}
				});
			return objectA;
		}
		// If there's a conflict or non-object nodes, `B` overwrites `A`
		return nodeB;
	}

	// eg [ " datasetId | modelId "] => ["datasetId", "modelId"]
	public static List<String> splitAndTrim(final String input) {
		return Arrays.stream(input.split("\\|")).map(String::trim).collect(Collectors.toList());
	}

	public static List<String> findIntersection(final List<String> list1, final List<String> list2) {
		final List<String> intersection = new ArrayList<>(list1);
		intersection.retainAll(list2); // Retain only elements that are in both lists
		return intersection;
	}
}
