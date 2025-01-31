package software.uncharted.terarium.hmiserver.controller.dataservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.dataservice.CsvAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseStatus;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.proxies.jsdelivr.JsDelivrProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.gollm.DatasetStatistics;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/datasets")
@RestController
@Slf4j
@RequiredArgsConstructor
public class DatasetController {

	private static final int DEFAULT_CSV_LIMIT = 100;

	final Config config;

	final DatasetService datasetService;
	final DatasetStatistics datasetStatistics;

	final JsDelivrProxy githubProxy;

	final ProjectService projectService;
	final CurrentUserService currentUserService;
	final Messages messages;

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Dataset created.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the dataset", content = @Content)
		}
	)
	public ResponseEntity<Dataset> createDataset(
		@RequestBody final Dataset dataset,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(datasetService.createAsset(dataset, projectId, permission));
		} catch (final IOException e) {
			log.error("Unable to create dataset", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets dataset by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dataset found.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "There was no dataset found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the dataset from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<Dataset> getDataset(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final String userId = currentUserService.get().getId();
		final Schema.Permission permission = projectService.checkPermissionCanReadOrNone(userId, projectId);

		try {
			Dataset dataset = datasetService
				.getAsset(id, permission)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("dataset.not-found")));

			// GETs not associated to a projectId cannot read private or temporary assets
			if (permission.equals(Schema.Permission.NONE) && (!dataset.getPublicAsset() || dataset.getTemporary())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-read"));
			}

			// If the user as write permission, and the stats are not present, calculate them
			final Schema.Permission permissionCanWrite = projectService.checkPermissionCanWrite(userId, projectId);
			if (
				permissionCanWrite.equals(Schema.Permission.WRITE) &&
				dataset.getColumns().stream().anyMatch(column -> column.getStats() == null)
			) {
				// Calculate the statistics for the columns
				final Optional<PresignedURL> datasetUrl = datasetService.getDownloadUrl(
					dataset.getId(),
					dataset.getFileNames().get(0)
				);

				if (datasetUrl.isEmpty()) {
					log.warn("Error calculating statistics for dataset {}", dataset.getId());
				} else {
					try {
						datasetStatistics.add(dataset, datasetUrl.get());

						// Update and fetch updated dataset
						datasetService.updateAsset(dataset, projectId, Schema.Permission.WRITE);
						Optional<Dataset> updatedDataset = datasetService.getAsset(id, permission);

						if (updatedDataset.isEmpty()) {
							log.warn("Failed to get dataset after update");
						} else {
							dataset = updatedDataset.get();
						}
					} catch (final Exception e) {
						log.error("Error calculating statistics for dataset {}", dataset.getId(), e);
					}
				}
			}

			return ResponseEntity.ok(dataset);
		} catch (final Exception e) {
			log.error("Unable to get dataset", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Delete dataset",
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
	public ResponseEntity<ResponseDeleted> deleteDataset(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			datasetService.deleteAsset(id, projectId, permission);
			return ResponseEntity.ok(new ResponseDeleted("Dataset", id));
		} catch (final IOException e) {
			log.error("Unable to delete dataset", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("dataset.unable-to-delete")
			);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dataset updated.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Dataset could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the dataset", content = @Content)
		}
	)
	ResponseEntity<Dataset> updateDataset(
		@PathVariable("id") final UUID id,
		@RequestBody final Dataset dataset,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			dataset.setId(id);
			final Optional<Dataset> updated = datasetService.updateAsset(dataset, projectId, permission);
			return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final IOException e) {
			log.error("Unable to update a dataset", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("dataset.unable-to-update")
			);
		}
	}

	@GetMapping("/{id}/download-csv")
	@Secured(Roles.USER)
	@Operation(summary = "Download dataset CSV")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dataset CSV.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CsvAsset.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the dataset from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<CsvAsset> getCsv(
		@PathVariable("id") final UUID datasetId,
		@RequestParam("filename") final String filename,
		@RequestParam(name = "limit", defaultValue = "" + DEFAULT_CSV_LIMIT, required = false) final Integer limit
	) {
		final CSVParser csvParser;
		try {
			csvParser = datasetService.getCSVFileParser(filename, datasetId);
			if (csvParser == null) {
				log.error("Unable to get CSV");
				throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					messages.get("dataset.not-found")
				);
			}
		} catch (final IOException e) {
			log.error("Unable to parse CSV", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}

		int rowcount = 0;
		final List<List<String>> csv = new ArrayList<>();

		for (CSVRecord record : csvParser.getRecords()) {
			if (rowcount < limit || limit <= 0) {
				csv.add(new ArrayList<>(record.toList()));
			}
			rowcount++;
		}

		final CsvAsset csvAsset = new CsvAsset(csv, new ArrayList<>(csvParser.getHeaderMap().keySet()), rowcount);

		final CacheControl cacheControl = CacheControl.maxAge(
			config.getCacheHeadersMaxAge(),
			TimeUnit.SECONDS
		).cachePublic();
		return ResponseEntity.ok().cacheControl(cacheControl).body(csvAsset);
	}

	@GetMapping("/{id}/download-file")
	@Secured(Roles.USER)
	@Operation(summary = "Download an arbitrary dataset file")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Dataset file.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CsvAsset.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the dataset from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<StreamingResponseBody> getFile(
		@PathVariable("id") final UUID datasetId,
		@RequestParam("filename") final String filename
	) {
		return datasetService.getDownloadStream(datasetId, filename);
	}

	@GetMapping("/{id}/download-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to download the dataset file")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Presigned url generated.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PresignedURL.class)
				)
			),
			@ApiResponse(
				responseCode = "404",
				description = "Dataset could not be found to create a URL for",
				content = @Content
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the presigned url",
				content = @Content
			)
		}
	)
	public ResponseEntity<PresignedURL> getDownloadURL(
		@PathVariable("id") final UUID id,
		@RequestParam("filename") final String filename,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanRead(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<Dataset> dataset;
		try {
			dataset = datasetService.getAsset(id, permission);
			if (dataset.isEmpty()) {
				throw new ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND,
					messages.get("dataset.not-found")
				);
			}
		} catch (final Exception e) {
			final String error = "Unable to get dataset";
			log.error(error, e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}

		if (
			dataset.get().getEsgfId() != null &&
			!dataset.get().getEsgfId().isEmpty() &&
			dataset.get().getDatasetUrls() != null &&
			!dataset.get().getDatasetUrls().isEmpty()
		) {
			final String url = dataset
				.get()
				.getDatasetUrls()
				.stream()
				.filter(fileUrl -> fileUrl.endsWith(filename))
				.findFirst()
				.orElse(null);

			if (url == null) {
				final String error = "The file " + filename + " was not found in the dataset";
				log.error(error);
				throw new ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND,
					messages.get("dataset.file-not-found")
				);
			}

			final PresignedURL presigned = new PresignedURL().setUrl(url).setMethod("GET");
			return ResponseEntity.ok(presigned);
		} else {
			try {
				final Optional<PresignedURL> url = datasetService.getDownloadUrl(id, filename);
				return url.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
			} catch (final Exception e) {
				final String error = "Unable to get download url";
				log.error(error, e);
				throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					messages.get("postgres.service-unavailable")
				);
			}
		}
	}

	/**
	 * Uploads a CSV file from GitHub given the path and owner name, then uploads
	 * it to the dataset.
	 */
	@PutMapping("/{id}/upload-csv-from-github")
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a CSV file from github to a dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Uploaded the CSV file.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the CSV", content = @Content)
		}
	)
	public ResponseEntity<ResponseStatus> uploadCsvFromGithub(
		@PathVariable("id") final UUID datasetId,
		@RequestParam("path") final String path,
		@RequestParam("repo-owner-and-name") final String repoOwnerAndName,
		@RequestParam("filename") final String filename,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		log.debug("Uploading CSV file from github to dataset {}", datasetId);

		// download CSV from GitHub
		final String csvString = githubProxy.getGithubCode(repoOwnerAndName, path).getBody();

		if (csvString == null) {
			final String error = "Unable to download csv from github";
			log.error(error);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("dataset.download-error")
			);
		}

		CSVParser csvParser;
		try {
			csvParser = new CSVParser(
				new StringReader(csvString),
				CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader().setSkipHeaderRecord(false).build()
			);
		} catch (IOException e) {
			log.error("Unable to parse csv from github", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("dataset.parse-error")
			);
		}

		List<String> headers = new ArrayList<>(csvParser.getHeaderMap().keySet());
		final HttpEntity csvEntity = new StringEntity(csvString, ContentType.APPLICATION_OCTET_STREAM);
		return uploadCSVAndUpdateColumns(datasetId, projectId, filename, csvEntity, headers, permission);
	}

	/**
	 * Uploads a CSV file to the dataset. This will grab a presigned URL from TDS
	 * then push the file to S3.
	 *
	 * @param datasetId ID of the dataset to upload t
	 * @param filename  CSV file to upload
	 * @return Response
	 */
	@PutMapping(value = "/{id}/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a CSV file to a dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Uploaded the CSV file.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the CSV", content = @Content)
		}
	)
	public ResponseEntity<ResponseStatus> uploadCsv(
		@PathVariable("id") final UUID datasetId,
		@RequestParam("filename") final String filename,
		@RequestPart("file") final MultipartFile input,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			log.debug("Uploading CSV file to dataset {}", datasetId);

			final byte[] csvBytes = input.getBytes();

			final HttpEntity csvEntity = new ByteArrayEntity(csvBytes, ContentType.APPLICATION_OCTET_STREAM);

			Reader targetReader = new InputStreamReader(new ByteArrayInputStream(csvBytes));
			CSVParser csvParser = new CSVParser(
				targetReader,
				CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader().setSkipHeaderRecord(false).build()
			);
			List<String> headers = new ArrayList<>(csvParser.getHeaderMap().keySet());

			return uploadCSVAndUpdateColumns(datasetId, projectId, filename, csvEntity, headers, permission);
		} catch (final IOException e) {
			final String error = "Unable to upload csv dataset";
			log.error(error, e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
	}

	@PutMapping(value = "/{id}/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	@Operation(summary = "Uploads an arbitrary file to a dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Uploaded the file.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the file", content = @Content)
		}
	)
	public ResponseEntity<Void> uploadData(
		@PathVariable("id") final UUID datasetId,
		@RequestParam("filename") final String filename,
		@RequestPart("file") final MultipartFile input,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			log.debug("Uploading file to dataset {}", datasetId);

			final ResponseEntity<Void> res = datasetService.getUploadStream(datasetId, filename, input);
			if (res.getStatusCode() == HttpStatus.OK) {
				// add the filename to existing file names
				Optional<Dataset> updatedDataset = datasetService.getAsset(datasetId, permission);
				if (updatedDataset.isEmpty()) {
					final String error = "Failed to get dataset after upload";
					log.error(error);
					throw new ResponseStatusException(
						org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
						messages.get("dataset.not-found")
					);
				}

				if (!updatedDataset.get().getFileNames().contains(filename)) {
					updatedDataset.get().getFileNames().add(filename);
				}

				try {
					updatedDataset = Optional.of(datasetService.extractColumnsFromFiles(updatedDataset.get()));
				} catch (final IOException e) {
					final String error = "Unable to extract columns from dataset";
					log.error(error, e);
					// This doesn't actually warrant a 500 since its just column metadata, so we'll
					// let it pass.
				}

				datasetService.updateAsset(updatedDataset.get(), projectId, permission);
			}

			return res;
		} catch (final IOException e) {
			final String error = "Unable to upload file to dataset";
			log.error(error, e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
	}

	@GetMapping("/{id}/upload-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to upload the dataset")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Presigned url generated.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PresignedURL.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the presigned url",
				content = @Content
			)
		}
	)
	@Deprecated
	public ResponseEntity<PresignedURL> getUploadURL(
		@PathVariable("id") final UUID id,
		@RequestParam("filename") final String filename
	) {
		try {
			return ResponseEntity.ok(datasetService.getUploadUrl(id, filename));
		} catch (final Exception e) {
			final String error = "Unable to get upload url";
			log.error(error, e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
	}

	/**
	 * Uploads a CSV file to the dataset. This will grab a presigned URL from TDS
	 * then push the file to S3 via a
	 * presigned URL and update the dataset with the headers.
	 *
	 * <p>
	 * If the headers fail to update there will be a print to the log, however, the
	 * response will just be the status
	 * of the original csv upload.
	 *
	 * @param datasetId ID of the dataset to upload to
	 * @param filename  CSV file to upload
	 * @param csvEntity CSV file as an HttpEntity
	 * @param headers   headers of the CSV file
	 * @return Response from the upload
	 */
	private ResponseEntity<ResponseStatus> uploadCSVAndUpdateColumns(
		final UUID datasetId,
		final UUID projectId,
		final String filename,
		final HttpEntity csvEntity,
		final List<String> headers,
		final Schema.Permission hasWritePermission
	) {
		try {
			// upload CSV to S3
			final Integer status = datasetService.uploadFile(datasetId, filename, csvEntity);

			// update dataset with headers if the previous upload was successful
			if (status == HttpStatus.OK.value()) {
				log.debug("Successfully uploaded CSV file to dataset {}. Now updating with headers", datasetId);

				final Optional<Dataset> updatedDataset = datasetService.getAsset(datasetId, hasWritePermission);
				if (updatedDataset.isEmpty()) {
					log.error("Failed to get dataset {} after upload", datasetId);
					return ResponseEntity.internalServerError().build();
				}

				DatasetService.addDatasetColumns(updatedDataset.get(), filename, headers);

				// add the filename to existing file names
				if (!updatedDataset.get().getFileNames().contains(filename)) {
					updatedDataset.get().getFileNames().add(filename);
				}

				// Calculate the statistics for the columns
				try {
					final PresignedURL datasetUrl = datasetService
						.getDownloadUrl(updatedDataset.get().getId(), filename)
						.orElseThrow(() -> new IllegalArgumentException(messages.get("dataset.download.url.not.found")));

					datasetStatistics.add(updatedDataset.get(), datasetUrl);
				} catch (final Exception e) {
					log.error("Error calculating statistics for dataset {}", updatedDataset.get().getId(), e);
				}

				datasetService.updateAsset(updatedDataset.get(), projectId, hasWritePermission);
			}

			return ResponseEntity.ok(new ResponseStatus(status));
		} catch (final IOException e) {
			log.error("Unable to upload csv data", e);
			throw new ResponseStatusException(
				org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
				messages.get("postgres.service-unavailable")
			);
		}
	}
}
