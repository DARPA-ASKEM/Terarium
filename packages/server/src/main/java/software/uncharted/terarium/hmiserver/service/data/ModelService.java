package software.uncharted.terarium.hmiserver.service.data;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.TerariumAssetEmbeddings;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelDescription;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelParameter;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.Annotations;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Observable;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.State;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.semantics.Transition;
import software.uncharted.terarium.hmiserver.models.dataservice.regnet.RegNetVertex;
import software.uncharted.terarium.hmiserver.models.task.CompoundTask;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.proxies.mira.MIRAProxy;
import software.uncharted.terarium.hmiserver.repository.data.ModelRepository;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;
import software.uncharted.terarium.hmiserver.service.gollm.EmbeddingService;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService;
import software.uncharted.terarium.hmiserver.service.tasks.TaskUtilities;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@Slf4j
@Service
public class ModelService extends TerariumAssetServiceWithSearch<Model, ModelRepository> {

	private final CurrentUserService currentUserService;
	private final DocumentAssetService documentAssetService;
	private final TaskService taskService;
	private final MIRAProxy miraProxy;

	public ModelService(
		final Config config,
		final CurrentUserService currentUserService,
		final DocumentAssetService documentAssetService,
		final ElasticsearchConfiguration elasticConfig,
		final ElasticsearchService elasticService,
		final EmbeddingService embeddingService,
		final Environment env,
		final MIRAProxy miraProxy,
		final ModelRepository repository,
		final ObjectMapper objectMapper,
		final ProjectAssetService projectAssetService,
		final ProjectService projectService,
		final S3ClientService s3ClientService,
		final TaskService taskService
	) {
		super(
			objectMapper,
			config,
			elasticConfig,
			elasticService,
			embeddingService,
			env,
			projectService,
			projectAssetService,
			s3ClientService,
			repository,
			Model.class
		);
		this.currentUserService = currentUserService;
		this.documentAssetService = documentAssetService;
		this.miraProxy = miraProxy;
		this.taskService = taskService;
	}

	@Observed(name = "function_profile")
	public List<ModelDescription> getDescriptions(final Integer page, final Integer pageSize) throws IOException {
		final SourceConfig source = new SourceConfig.Builder()
			.filter(new SourceFilter.Builder().excludes("model", "semantics").build())
			.build();

		final SearchRequest req = new SearchRequest.Builder()
			.index(getAssetAlias())
			.from(page)
			.size(pageSize)
			.query(q ->
				q.bool(b ->
					b
						.mustNot(mn -> mn.exists(e -> e.field("deletedOn")))
						.mustNot(mn -> mn.term(t -> t.field("temporary").value(true)))
						.mustNot(mn -> mn.term(t -> t.field("isPublic").value(false)))
				)
			)
			.source(source)
			.build();

		return elasticService.search(req, Model.class).stream().map(m -> ModelDescription.fromModel(m)).toList();
	}

	@Observed(name = "function_profile")
	public Optional<ModelDescription> getDescription(final UUID id, final Schema.Permission hasReadPermission)
		throws IOException {
		final Optional<Model> model = getAsset(id, hasReadPermission);
		if (model.isPresent()) {
			final ModelDescription md = ModelDescription.fromModel(model.get());
			return Optional.of(md);
		}

		return Optional.empty();
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetIndex() {
		return elasticConfig.getModelIndex();
	}

	@Override
	@Observed(name = "function_profile")
	protected String getAssetPath() {
		throw new UnsupportedOperationException("Models are not stored in S3");
	}

	@Override
	public String getAssetAlias() {
		return elasticConfig.getModelAlias();
	}

	@Observed(name = "function_profile")
	public Optional<Model> getModelFromModelConfigurationId(
		final UUID modelConfigurationId,
		final Schema.Permission hasReadPermission
	) {
		return repository.findModelByModelConfigurationId(modelConfigurationId);
	}

	@Override
	@Observed(name = "function_profile")
	public Model createAsset(final Model asset, final UUID projectId, final Schema.Permission hasWritePermission)
		throws IOException {
		// Make sure that the model framework is set to lowercase
		if (asset.getHeader() != null && asset.getHeader().getSchemaName() != null) asset
			.getHeader()
			.setSchemaName(asset.getHeader().getSchemaName().toLowerCase());

		// Set default value for model parameters (0.0)
		asset
			.getParameters()
			.forEach(param -> {
				if (param.getValue() == null) {
					param.setValue(1.0);
				}
			});

		// Force proper annotation metadata
		final ModelMetadata metadata = asset.getMetadata();
		if (metadata.getAnnotations() == null) {
			metadata.setAnnotations(new Annotations());
			asset.setMetadata(metadata);
		}

		if (asset.getHeader() != null && asset.getHeader().getName() != null) {
			asset.setName(asset.getHeader().getName());
		}
		final Model created = super.createAsset(asset, projectId, hasWritePermission);

		if (!isRunningTestProfile() && created.getPublicAsset() && !created.getTemporary()) {
			new Thread(() -> {
				try {
					final TerariumAssetEmbeddings embeddings = embeddingService.generateEmbeddings(
						created.getEmbeddingSourceText()
					);

					// Execute the update request
					uploadEmbeddings(created.getId(), embeddings, hasWritePermission);
				} catch (final Exception e) {
					log.error("Failed to update embeddings for model {}", created.getId(), e);
				}
			}).start();
		}

		return created;
	}

	@Override
	@Observed(name = "function_profile")
	public Optional<Model> updateAsset(
		final Model asset,
		final UUID projectId,
		final Schema.Permission hasWritePermission
	) throws IOException, IllegalArgumentException {
		if (asset.getHeader() != null && asset.getHeader().getName() != null) {
			asset.setName(asset.getHeader().getName());
		}

		// Force proper annotation metadata
		final ModelMetadata metadata = asset.getMetadata();
		if (metadata.getAnnotations() == null) {
			metadata.setAnnotations(new Annotations());
			asset.setMetadata(metadata);
		}

		final Optional<Model> updatedOptional = super.updateAsset(asset, projectId, hasWritePermission);
		if (updatedOptional.isEmpty()) {
			return Optional.empty();
		}

		final Model updated = updatedOptional.get();

		if (!isRunningTestProfile() && updated.getPublicAsset() && !updated.getTemporary()) {
			new Thread(() -> {
				try {
					final TerariumAssetEmbeddings embeddings = embeddingService.generateEmbeddings(
						updated.getEmbeddingSourceText()
					);

					// Execute the update request
					uploadEmbeddings(updated.getId(), embeddings, hasWritePermission);
				} catch (final Exception e) {
					log.error("Failed to update embeddings for model {}", updated.getId(), e);
				}
			}).start();
		}

		return updatedOptional;
	}

	public UUID enrichModel(
		final UUID projectId,
		final UUID documentId,
		final UUID modelId,
		final Schema.Permission permission,
		final boolean overwrite
	) throws IOException, ExecutionException, InterruptedException, TimeoutException {
		// Grab the document if it exists
		final Optional<DocumentAsset> document = documentAssetService.getAsset(documentId, permission);

		// make sure there is text in the document
		if (document.isPresent() && (document.get().getText() == null || document.get().getText().isEmpty())) {
			final String errorString = String.format("Document %s has no extracted text", documentId);
			log.warn(errorString);
			throw new IOException(errorString);
		}

		// Grab the model
		final Optional<Model> model = getAsset(modelId, permission);
		if (model.isEmpty()) {
			final String errorString = String.format("Model %s not found", modelId);
			log.warn(errorString);
			throw new IOException(errorString);
		}

		// stripping the metadata from the model before its sent since it can cause
		// gollm to fail with massive inputs
		model.get().setMetadata(new ModelMetadata());

		final TaskRequest req;

		if (document.isPresent()) {
			// Create the tasks
			final TaskRequest enrichAmrRequest = TaskUtilities.getEnrichAMRTaskRequest(
				currentUserService.get().getId(),
				document.get(),
				model.get(),
				projectId,
				overwrite
			);

			final TaskRequest modelCardRequest = TaskUtilities.getModelCardTask(
				currentUserService.get().getId(),
				document.get(),
				model.get(),
				projectId
			);

			req = new CompoundTask(enrichAmrRequest, modelCardRequest);
		} else {
			req = TaskUtilities.getModelCardTask(currentUserService.get().getId(), null, model.get(), projectId);
		}

		final TaskResponse resp = taskService.runTask(TaskService.TaskMode.SYNC, req);

		// at this point the initial enrichment has happened.
		final Optional<Model> newModel = getAsset(modelId, permission);
		if (newModel.isEmpty()) {
			final String errorString = String.format("Model %s not found", modelId);
			log.warn(errorString);
			throw new IOException(errorString);
		}

		// Update State Grounding
		if (newModel.get().isRegnet()) {
			final List<RegNetVertex> vertices = newModel.get().getVerticies();
			vertices.forEach(vertex -> {
				if (vertex == null) {
					vertex = new RegNetVertex();
				}
				TaskUtilities.performDKGSearchAndSetGrounding(miraProxy, vertex);
			});
			newModel.get().setVerticies(vertices);
		} else {
			final List<State> states = newModel.get().getStates();
			states.forEach(state -> {
				if (state == null) {
					state = new State();
				}
				TaskUtilities.performDKGSearchAndSetGrounding(miraProxy, state);
			});
			newModel.get().setStates(states);
		}

		// Update Observable Grounding
		if (newModel.get().getObservables() != null && !newModel.get().getObservables().isEmpty()) {
			final List<Observable> observables = newModel.get().getObservables();
			observables.forEach(observable -> {
				if (observable == null) {
					observable = new Observable();
				}
				TaskUtilities.performDKGSearchAndSetGrounding(miraProxy, observable);
			});
			newModel.get().setObservables(observables);
		}

		// Update Parameter Grounding
		if (newModel.get().getParameters() != null && !newModel.get().getParameters().isEmpty()) {
			final List<ModelParameter> parameters = newModel.get().getParameters();
			parameters.forEach(parameter -> {
				if (parameter == null) {
					parameter = new ModelParameter();
				}
				TaskUtilities.performDKGSearchAndSetGrounding(miraProxy, parameter);
			});
			newModel.get().setParameters(parameters);
		}

		// Update Transition Grounding
		if (newModel.get().getTransitions() != null && !newModel.get().getTransitions().isEmpty()) {
			final List<Transition> transitions = newModel.get().getTransitions();
			transitions.forEach(transition -> {
				if (transition == null) {
					transition = new Transition();
				}
				TaskUtilities.performDKGSearchAndSetGrounding(miraProxy, transition);
			});
			newModel.get().setTransitions(transitions);
		}

		try {
			updateAsset(newModel.get(), projectId, permission);
		} catch (final IOException e) {
			final String errorString = String.format("Failed to update model %s", modelId);
			log.warn(errorString);
			throw new IOException(errorString);
		}

		return newModel.get().getId();
	}
}
