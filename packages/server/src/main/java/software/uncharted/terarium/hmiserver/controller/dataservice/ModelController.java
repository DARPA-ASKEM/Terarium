package software.uncharted.terarium.hmiserver.controller.dataservice;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.dataservice.AssetType;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.model.Model;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelDescription;
import software.uncharted.terarium.hmiserver.models.dataservice.model.configurations.ModelConfiguration;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.ModelMetadata;
import software.uncharted.terarium.hmiserver.models.dataservice.modelparts.metadata.Annotations;
import software.uncharted.terarium.hmiserver.models.dataservice.project.Project;
import software.uncharted.terarium.hmiserver.models.dataservice.project.ProjectAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceQueryParam;
import software.uncharted.terarium.hmiserver.models.dataservice.provenance.ProvenanceType;
import software.uncharted.terarium.hmiserver.models.simulationservice.interventions.InterventionPolicy;
import software.uncharted.terarium.hmiserver.repository.data.InterventionRepository;
import software.uncharted.terarium.hmiserver.repository.data.ModelConfigRepository;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationService;
import software.uncharted.terarium.hmiserver.service.data.ModelService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.data.ProvenanceSearchService;
import software.uncharted.terarium.hmiserver.service.gollm.EmbeddingService;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/models")
@RestController
@Slf4j
@RequiredArgsConstructor
public class ModelController {

	final ModelService modelService;

	final DocumentAssetService documentAssetService;

	final ProvenanceSearchService provenanceSearchService;

	final ObjectMapper objectMapper;

	final DatasetService datasetService;

	final ProjectService projectService;

	final CurrentUserService currentUserService;

	final ProjectAssetService projectAssetService;

	final ModelConfigurationService modelConfigurationService;

	final Messages messages;

	final ModelConfigRepository modelConfigRepository;

	final InterventionRepository interventionRepository;

	final EmbeddingService embeddingService;

	@GetMapping("/descriptions")
	@Secured(Roles.USER)
	@Operation(summary = "Gets all model descriptions")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model descriptions found.",
				content = @Content(
					array = @ArraySchema(
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ModelDescription.class)
					)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving descriptions from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<List<ModelDescription>> listModels(
		@RequestParam(name = "page-size", defaultValue = "100", required = false) final Integer pageSize,
		@RequestParam(name = "page", defaultValue = "0", required = false) final Integer page
	) {
		try {
			return ResponseEntity.ok(modelService.getDescriptions(page, pageSize));
		} catch (final IOException e) {
			final String error = "Unable to get model";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/descriptions")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a model description by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model description found.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ModelDescription.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "There was no description found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the description from the data store",
				content = @Content
			)
		}
	)
	ResponseEntity<ModelDescription> getDescription(@PathVariable("id") final UUID id) {
		final UUID projectId = modelService.getProjectIdForAsset(id);
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		try {
			final Optional<ModelDescription> model = modelService.getDescription(id, permission);
			return model.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final IOException e) {
			final String error = "Unable to get model description";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a model by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model found.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Model.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the model from the data store",
				content = @Content
			)
		}
	)
	ResponseEntity<Model> getModel(@PathVariable("id") final UUID id) {
		final UUID projectId = modelService.getProjectIdForAsset(id);
		final Schema.Permission permission = projectService.checkPermissionCanReadOrNone(
			currentUserService.get().getId(),
			projectId
		);

		try {
			// Fetch the model from the data-service
			final Optional<Model> model = modelService.getAsset(id, permission);
			if (model.isEmpty()) {
				return ResponseEntity.noContent().build();
			}
			// GETs not associated to a projectId cannot read private or temporary assets
			if (permission.equals(Schema.Permission.NONE) && (!model.get().getPublicAsset() || model.get().getTemporary())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-read"));
			}

			// Find the Document Assets linked via provenance to the model
			final ProvenanceQueryParam body = new ProvenanceQueryParam();
			body.setRootId(id);
			body.setRootType(ProvenanceType.MODEL);
			body.setTypes(List.of(ProvenanceType.DOCUMENT));
			final Set<String> documentIds = provenanceSearchService.modelsFromDocument(body);
			if (!documentIds.isEmpty()) {
				// Make sure we have a metadata object
				if (model.get().getMetadata() == null) {
					model.get().setMetadata(new ModelMetadata());
				}
				// Make sure we have an attributes list
				if (model.get().getMetadata().getAttributes() == null) {
					model.get().getMetadata().setAttributes(new ArrayList<>());
				}

				documentIds.forEach(documentId -> {
					try {
						// Fetch the Document extractions
						final Optional<DocumentAsset> document = documentAssetService.getAsset(
							UUID.fromString(documentId),
							permission
						);
						if (document.isPresent()) {
							if (document.get().getMetadata() == null) {
								document.get().setMetadata(new HashMap<>());
							}
							final List<JsonNode> extractions = objectMapper.convertValue(
								document.get().getMetadata().get("attributes"),
								new TypeReference<>() {}
							);

							// Append the Document extractions to the Model extractions, just for the
							// front-end.
							// Those are NOT to be saved back to the data-service.
							if (extractions != null) {
								model.get().getMetadata().getAttributes().addAll(extractions);
							} else {
								log.error("No attributes added to Model as DocumentAsset ({}) has no attributes.", documentId);
							}
						}
					} catch (final Exception e) {
						log.error("Unable to get the document " + documentId, e);
					}
				});
			} else {
				log.debug("Unable to get the, or empty, provenance search models_from_document for model " + id);
			}

			// Force observable to empty-list if null or not specified
			if (model.get().getSemantics() != null) {
				if (model.get().getSemantics().getOde().getObservables() == null) {
					model.get().getSemantics().getOde().setObservables(new ArrayList());
				}
			}
			// Force proper annotation metadata
			final ModelMetadata metadata = model.get().getMetadata();
			if (metadata.getAnnotations() == null) {
				metadata.setAnnotations(new Annotations());
				model.get().setMetadata(metadata);
			}

			// Return the model
			return ResponseEntity.ok(model.get());
		} catch (final Exception e) {
			final String error = "Unable to get model";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/from-model-configuration/{model-config-id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a model by model configuration ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model found.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Model.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the model from the data store",
				content = @Content
			)
		}
	)
	ResponseEntity<Model> getModelFromConfigId(@PathVariable("model-config-id") final UUID id) {
		final UUID projectId = modelConfigurationService.getProjectIdForAsset(id);
		final Schema.Permission permission = projectService.checkPermissionCanReadOrNone(
			currentUserService.get().getId(),
			projectId
		);

		try {
			// Fetch the model from the data-service
			final Optional<Model> model = modelService.getModelFromModelConfigurationId(id, permission);
			if (model.isEmpty()) {
				return ResponseEntity.noContent().build();
			}
			// GETs not associated to a projectId cannot read private or temporary assets
			if (permission.equals(Schema.Permission.NONE) && (!model.get().getPublicAsset() || model.get().getTemporary())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-read"));
			}

			// Find the Document Assets linked via provenance to the model
			final ProvenanceQueryParam body = new ProvenanceQueryParam();
			body.setRootId(id);
			body.setRootType(ProvenanceType.MODEL);
			body.setTypes(List.of(ProvenanceType.DOCUMENT));
			final Set<String> documentIds = provenanceSearchService.modelsFromDocument(body);
			if (!documentIds.isEmpty()) {
				// Make sure we have a metadata object
				if (model.get().getMetadata() == null) {
					model.get().setMetadata(new ModelMetadata());
				}
				// Make sure we have an attributes list
				if (model.get().getMetadata().getAttributes() == null) {
					model.get().getMetadata().setAttributes(new ArrayList<>());
				}

				documentIds.forEach(documentId -> {
					try {
						// Fetch the Document extractions
						final Optional<DocumentAsset> document = documentAssetService.getAsset(
							UUID.fromString(documentId),
							permission
						);
						if (document.isPresent()) {
							if (document.get().getMetadata() == null) {
								document.get().setMetadata(new HashMap<>());
							}
							final List<JsonNode> extractions = objectMapper.convertValue(
								document.get().getMetadata().get("attributes"),
								new TypeReference<>() {}
							);

							// Append the Document extractions to the Model extractions, just for the
							// front-end.
							// Those are NOT to be saved back to the data-service.
							if (extractions != null) {
								model.get().getMetadata().getAttributes().addAll(extractions);
							} else {
								log.error("No attributes added to Model as DocumentAsset ({}) has no attributes.", documentId);
							}
						}
					} catch (final Exception e) {
						log.error("Unable to get the document " + documentId, e);
					}
				});
			} else {
				log.debug("Unable to get the, or empty, provenance search models_from_document for model " + id);
			}

			// Return the model
			return ResponseEntity.ok(model.get());
		} catch (final Exception e) {
			final String error = "Unable to get model";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/search")
	@Secured(Roles.USER)
	@Operation(summary = "Search models with a query")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Models found.",
				content = @Content(
					array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Model.class))
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving models from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<List<Model>> searchModels(
		@RequestBody final JsonNode queryJson,
		@RequestParam(name = "page-size", defaultValue = "100", required = false) final Integer pageSize,
		@RequestParam(name = "page", defaultValue = "0", required = false) final Integer page
	) {
		try {
			Query query = null;
			if (queryJson != null) {
				// if query is provided deserialize it, append the soft delete filter
				final byte[] bytes = objectMapper.writeValueAsString(queryJson).getBytes();
				query = new Query.Builder()
					.bool(b ->
						b
							.must(new Query.Builder().withJson(new ByteArrayInputStream(bytes)).build())
							.mustNot(mn -> mn.exists(e -> e.field("deletedOn")))
							.mustNot(mn -> mn.term(t -> t.field("temporary").value(true)))
					)
					.build();
			} else {
				query = new Query.Builder()
					.bool(b ->
						b
							.mustNot(mn -> mn.exists(e -> e.field("deletedOn")))
							.mustNot(mn -> mn.term(t -> t.field("temporary").value(true)))
					)
					.build();
			}

			final SourceConfig source = new SourceConfig.Builder()
				.filter(new SourceFilter.Builder().excludes("model", "semantics").build())
				.build();

			return ResponseEntity.ok(modelService.searchAssets(page, pageSize, query, source));
		} catch (final IOException e) {
			final String error = "Unable to search models";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a model")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model updated.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Model.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Model could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the model", content = @Content)
		}
	)
	ResponseEntity<Model> updateModel(@PathVariable("id") final UUID id, @RequestBody final Model model) {
		final UUID projectId = modelConfigurationService.getProjectIdForAsset(id);
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			final Optional<Model> originalModel = modelService.getAsset(id, permission);
			if (originalModel.isEmpty()) {
				return ResponseEntity.notFound().build();
			}

			model.setId(id);
			// Set the model name from the AMR header name.
			// TerariumAsset have a name field, but it's not used for the model name outside
			// the front-end.
			final Optional<Model> updated = modelService.updateAsset(model, projectId, permission);

			if (updated.isEmpty()) {
				return ResponseEntity.notFound().build();
			}

			return ResponseEntity.ok(updated.get());
		} catch (final IOException e) {
			final String error = "Unable to update model";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes an model")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Deleted model",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class)
					)
				}
			),
			@ApiResponse(responseCode = "500", description = "An error occurred while deleting", content = @Content)
		}
	)
	ResponseEntity<ResponseDeleted> deleteModel(@PathVariable("id") final UUID id) {
		final UUID projectId = modelConfigurationService.getProjectIdForAsset(id);
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			modelService.deleteAsset(id, projectId, permission);
			return ResponseEntity.ok(new ResponseDeleted("Model", id));
		} catch (final IOException e) {
			final String error = "Unable to delete model";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new model")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Model created.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Model.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the model", content = @Content)
		}
	)
	ResponseEntity<ProjectAsset> createModel(
		@RequestBody final Model model,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		final Model createdModel;

		try {
			// Set the model name from the AMR header name.
			// TerariumAsset have a name field, but it's not used for the model name outside
			// the front-end.
			model.setName(model.getHeader().getName());
			createdModel = modelService.createAsset(model, projectId, permission);

			// create default configuration
			final ModelConfiguration modelConfiguration = ModelConfigurationService.modelConfigurationFromAMR(
				createdModel,
				null,
				null
			);
			modelConfigurationService.createAsset(modelConfiguration, projectId, permission);

			// add default model configuration to project
			final Optional<Project> project = projectService.getProject(projectId);
			if (project.isPresent()) {
				projectAssetService.createProjectAsset(
					project.get().getId(),
					AssetType.MODEL_CONFIGURATION,
					modelConfiguration,
					permission
				);
			}
		} catch (final IOException e) {
			final String error = "Unable to create model";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}

		final AssetType assetType = AssetType.MODEL;
		final ProjectAsset projectAsset = projectAssetService.createProjectAsset(
			projectId,
			assetType,
			createdModel,
			permission
		);

		return ResponseEntity.status(HttpStatus.CREATED).body(projectAsset);
	}

	@GetMapping("/{id}/model-configurations")
	@Secured(Roles.USER)
	@Operation(summary = "Gets all model configurations for a model")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model configurations found",
				content = @Content(
					array = @ArraySchema(
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ModelConfiguration.class)
					)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving configurations from the data store",
				content = @Content
			)
		}
	)
	ResponseEntity<List<ModelConfiguration>> getModelConfigurationsForModelId(
		@PathVariable("id") final UUID id,
		@RequestParam(value = "page", required = false, defaultValue = "0") final int page,
		@RequestParam(value = "page-size", required = false, defaultValue = "100") final int pageSize
	) {
		final UUID projectId = modelService.getProjectIdForAsset(id);
		projectService.checkPermissionCanRead(currentUserService.get().getId(), projectId);

		try {
			final List<ModelConfiguration> modelConfigurations =
				modelConfigRepository.findByModelIdAndDeletedOnIsNullAndTemporaryFalseOrderByCreatedOnAsc(
					id,
					PageRequest.of(page, pageSize)
				);

			return ResponseEntity.ok(modelConfigurations);
		} catch (final Exception e) {
			final String error = "Unable to get model configurations";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/intervention-policies")
	@Secured(Roles.USER)
	@Operation(summary = "Gets all intervention policies for a model")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Interventions policies found.",
				content = @Content(
					array = @ArraySchema(
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = InterventionPolicy.class)
					)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving policies from the data store",
				content = @Content
			)
		}
	)
	ResponseEntity<List<InterventionPolicy>> getInterventionsForModelId(
		@PathVariable("id") final UUID id,
		@RequestParam(value = "page", required = false, defaultValue = "0") final int page,
		@RequestParam(value = "page-size", required = false, defaultValue = "100") final int pageSize
	) {
		final UUID projectId = modelService.getProjectIdForAsset(id);
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		try {
			final List<InterventionPolicy> interventionPolicies =
				interventionRepository.findByModelIdAndDeletedOnIsNullAndTemporaryFalse(id, PageRequest.of(page, pageSize));

			return ResponseEntity.ok(interventionPolicies);
		} catch (final Exception e) {
			final String error = "Unable to get intervention policies";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping("/amr-to-model-configuration")
	@Secured(Roles.USER)
	@Operation(summary = "Formats a model configuration from an AMR")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Model configurations found.",
				content = @Content(
					array = @ArraySchema(
						schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ModelConfiguration.class)
					)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving configurations from the data store",
				content = @Content
			)
		}
	)
	ResponseEntity<ModelConfiguration> modelConfigurationFromAmr(
		@RequestBody final Model model,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		try {
			final Schema.Permission permission = projectService.checkPermissionCanRead(
				currentUserService.get().getId(),
				projectId
			);

			final ModelConfiguration modelConfiguration = ModelConfigurationService.modelConfigurationFromAMR(
				model,
				model.getName(),
				model.getDescription()
			);
			return ResponseEntity.ok(modelConfiguration);
		} catch (final Exception e) {
			final String error = "Unable to get model configurations";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}
}
