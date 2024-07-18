package software.uncharted.terarium.hmiserver.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.Workflow;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowEdge;
import software.uncharted.terarium.hmiserver.models.dataservice.workflow.WorkflowNode;
import software.uncharted.terarium.hmiserver.repository.data.WorkflowRepository;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Service
public class WorkflowService extends TerariumAssetServiceWithSearch<Workflow, WorkflowRepository> {

	public WorkflowService(
		final ObjectMapper objectMapper,
		final Config config,
		final ElasticsearchConfiguration elasticConfig,
		final ElasticsearchService elasticService,
		final ProjectService projectService,
		final ProjectAssetService projectAssetService,
		final S3ClientService s3ClientService,
		final WorkflowRepository repository
	) {
		super(
			objectMapper,
			config,
			elasticConfig,
			elasticService,
			projectService,
			projectAssetService,
			s3ClientService,
			repository,
			Workflow.class
		);
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetIndex() {
		return elasticConfig.getWorkflowIndex();
	}

	@Override
	@Observed(name = "function_profile")
	public String getAssetAlias() {
		return elasticConfig.getWorkflowAlias();
	}

	@Override
	@Observed(name = "function_profile")
	public Workflow createAsset(
		final Workflow asset,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException, IllegalArgumentException {
		// ensure the workflow id is set correctly
		if (asset.getNodes() != null) {
			for (final WorkflowNode node : asset.getNodes()) {
				node.setWorkflowId(asset.getId());
			}
		}
		if (asset.getEdges() != null) {
			for (final WorkflowEdge edge : asset.getEdges()) {
				edge.setWorkflowId(asset.getId());
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
		// ensure the workflow id is set correctly
		if (asset.getNodes() != null) {
			for (final WorkflowNode node : asset.getNodes()) {
				node.setWorkflowId(asset.getId());
			}
		}
		if (asset.getEdges() != null) {
			for (final WorkflowEdge edge : asset.getEdges()) {
				edge.setWorkflowId(asset.getId());
			}
		}
		return super.updateAsset(asset, projectId, hasWritePermission);
	}

	@Override
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Workflows are not stored in S3");
	}
}
