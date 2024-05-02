package software.uncharted.terarium.hmiserver.controller.dataservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.math.Quantiles;
import com.google.common.math.Stats;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.CsvAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.CsvColumnStats;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseStatus;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.Dataset;
import software.uncharted.terarium.hmiserver.models.dataservice.dataset.DatasetColumn;
import software.uncharted.terarium.hmiserver.proxies.climatedata.ClimateDataProxy;
import software.uncharted.terarium.hmiserver.proxies.jsdelivr.JsDelivrProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.data.DatasetService;

@RequestMapping("/datasets")
@RestController
@Slf4j
@RequiredArgsConstructor
public class DatasetController {

	private static final int DEFAULT_CSV_LIMIT = 100;

	final DatasetService datasetService;
	final ClimateDataProxy climateDataProxy;

	final JsDelivrProxy githubProxy;

	private final List<String> SEARCH_FIELDS = List.of("name", "description");

	@GetMapping
	@Secured(Roles.USER)
	@Operation(summary = "Gets all datasets")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Datasets found.", content = @Content(array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class)))),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving datasets from the data store", content = @Content)
	})
	public ResponseEntity<List<Dataset>> getDatasets(
			@RequestParam(name = "page-size", defaultValue = "100", required = false) final Integer pageSize,
			@RequestParam(name = "page", defaultValue = "0", required = false) final Integer page,
			@RequestParam(name = "terms", defaultValue = "", required = false) final String terms) {
		try {

			List<String> ts = new ArrayList<>();
			if (terms != null && !terms.isEmpty()) {
				ts = Arrays.asList(terms.split("[,\\s]"));
			}

			Query query = null;

			if (!ts.isEmpty()) {

				final List<FieldValue> values = new ArrayList<>();
				for (final String term : ts) {
					values.add(FieldValue.of(term));
				}

				final TermsQueryField termsQueryField = new TermsQueryField.Builder().value(values).build();

				final List<TermsQuery> shouldQueries = new ArrayList<>();

				for (final String field : SEARCH_FIELDS) {

					final TermsQuery termsQuery = new TermsQuery.Builder()
							.field(field)
							.terms(termsQueryField)
							.build();

					shouldQueries.add(termsQuery);
				}

				query = new Query.Builder()
						.bool(b -> {
							shouldQueries.forEach(sq -> b.should(s -> s.terms(sq)));
							return b;
						})
						.build();
			}

			if (query == null) {
				return ResponseEntity.ok(datasetService.getAssets(page, pageSize));
			} else {
				return ResponseEntity.ok(datasetService.searchAssets(page, pageSize, query));
			}

		} catch (final IOException e) {
			final String error = "Unable to get datasets";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Dataset created.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the dataset", content = @Content)
	})
	public ResponseEntity<Dataset> createDataset(@RequestBody final Dataset dataset) {

		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(datasetService.createAsset(dataset));
		} catch (final IOException e) {
			final String error = "Unable to create dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets dataset by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Dataset found.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class))),
			@ApiResponse(responseCode = "404", description = "There was no dataset found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the dataset from the data store", content = @Content)
	})
	public ResponseEntity<Dataset> getDataset(@PathVariable("id") final UUID id) {
		try {
			Optional<Dataset> dataset = datasetService.getAsset(id);
			try {
				dataset = extractColumnsAsNeededAndSave(dataset);
			} catch (final IOException e) {
				final String error = "Unable to extract columns from dataset";
				log.error(error, e);
				// This doesn't actually warrant a 500 since its just column metadata, so we'll
				// let it pass.
			}
			return dataset.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final Exception e) {
			final String error = "Unable to get dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Extracts columns from the dataset if they are not already set and saves the
	 * dataset.
	 *
	 * @param dataset dataset to extract columns from
	 * @return the dataset with columns extracted and saved
	 * @throws IOException if there is an issue saving the dataset after extracting
	 *                     columns
	 */
	private Optional<Dataset> extractColumnsAsNeededAndSave(final Optional<Dataset> dataset) throws IOException {
		if (dataset.isEmpty()) {
			// that's weird. let someone else handle this.
			return dataset;
		}

		if (dataset.get().getColumns() != null && !dataset.get().getColumns().isEmpty()) {
			// columns are set. No need to extract
			return dataset;
		}
		if (dataset.get().getFileNames() == null || dataset.get().getFileNames().isEmpty()) {
			// no file names to extract columns from
			return dataset;
		}

		for (final String filename : dataset.get().getFileNames()) {
			if (!filename.endsWith(".nc")) {
				try {
					final List<List<String>> csv = getCSVFile(filename, dataset.get().getId(), 1);
					if (csv == null || csv.isEmpty()) {
						continue;
					}
					updateHeaders(dataset.get(), csv.get(0));
				} catch (final IOException e) {
					final String error = "Unable to get dataset CSV for file " + filename;
					log.error(error, e);
					continue;
				}
			} else {
				return dataset;
			}
		}

		return datasetService.updateAsset(dataset.get());
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Delete dataset", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class))
			}),
			@ApiResponse(responseCode = "500", description = "An error occurred while deleting", content = @Content)
	})
	public ResponseEntity<ResponseDeleted> deleteDataset(@PathVariable("id") final UUID id) {

		try {
			datasetService.deleteAsset(id);
			return ResponseEntity.ok(new ResponseDeleted("Dataset", id));
		} catch (final IOException e) {
			final String error = "Unable to delete dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Dataset updated.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Dataset.class))),
			@ApiResponse(responseCode = "404", description = "Dataset could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the dataset", content = @Content)
	})
	ResponseEntity<Dataset> updateDataset(@PathVariable("id") final UUID id, @RequestBody final Dataset dataset) {

		try {
			dataset.setId(id);
			final Optional<Dataset> updated = datasetService.updateAsset(dataset);
			return updated.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final IOException e) {
			final String error = "Unable to update a dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/download-csv")
	@Secured(Roles.USER)
	@Operation(summary = "Download dataset CSV")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Dataset CSV.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CsvAsset.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the dataset from the data store", content = @Content)
	})
	public ResponseEntity<CsvAsset> getCsv(
			@PathVariable("id") final UUID datasetId,
			@RequestParam("filename") final String filename,
			@RequestParam(name = "limit", defaultValue = ""
					+ DEFAULT_CSV_LIMIT, required = false) final Integer limit) {

		final List<List<String>> csv;
		try {
			csv = getCSVFile(filename, datasetId, limit);
			if (csv == null) {
				final String error = "Unable to get CSV";
				log.error(error);
				throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
			}
		} catch (final IOException e) {
			final String error = "Unable to parse CSV";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}

		final List<String> headers = csv.get(0);
		final List<CsvColumnStats> csvColumnStats = new ArrayList<>();
		for (int i = 0; i < csv.get(0).size(); i++) {
			final List<String> column = getColumn(csv, i);
			csvColumnStats.add(getStats(column.subList(1, column.size()))); // remove first as it is header:
		}

		final int linesToRead = limit != null ? (limit == -1 ? csv.size() : limit) : DEFAULT_CSV_LIMIT;

		final CsvAsset csvAsset = new CsvAsset(
				csv.subList(0, Math.min(linesToRead + 1, csv.size())), csvColumnStats, headers, csv.size());

		return ResponseEntity.ok(csvAsset);
	}

	private List<List<String>> getCSVFile(final String filename, final UUID datasetId, final Integer limit)
			throws IOException {
		String rawCSV = "";
		final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build();

		final Optional<PresignedURL> url = datasetService.getDownloadUrl(datasetId, filename);
		if (url.isEmpty()) {
			return null;
		}
		final PresignedURL presignedURL = url.get();
		final HttpGet get = new HttpGet(Objects.requireNonNull(presignedURL).getUrl());
		final HttpResponse response = httpclient.execute(get);
		final BufferedReader reader = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent(), "utf-8"));

		String line = null;
		Integer count = 0;
		while ((line = reader.readLine()) != null) {
			if (limit > 0 && count > limit) {
				break;
			}
			rawCSV += line + '\n';
			count++;
		}

		final List<List<String>> csv;
		csv = csvToRecords(rawCSV);
		return csv;
	}

	@GetMapping("/{id}/download-file")
	@Secured(Roles.USER)
	@Operation(summary = "Download an arbitrary dataset file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Dataset file.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CsvAsset.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the dataset from the data store", content = @Content)
	})
	public ResponseEntity<StreamingResponseBody> getFile(
			@PathVariable("id") final UUID datasetId, @RequestParam("filename") final String filename) {

		return datasetService.getDownloadStream(datasetId, filename);
	}

	@GetMapping("/{id}/download-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to download the dataset file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Presigned url generated.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PresignedURL.class))),
			@ApiResponse(responseCode = "404", description = "Dataset could not be found to create a URL for", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the presigned url", content = @Content)
	})
	public ResponseEntity<PresignedURL> getDownloadURL(
			@PathVariable("id") final UUID id, @RequestParam("filename") final String filename) {
		final Optional<Dataset> dataset;
		try {
			dataset = datasetService.getAsset(id);
			if (dataset.isEmpty()) {
				throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Dataset not found");
			}
		} catch (final Exception e) {
			final String error = "Unable to get dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}

		if (dataset.get().getEsgfId() != null
				&& !dataset.get().getEsgfId().isEmpty()
				&& dataset.get().getDatasetUrls() != null
				&& !dataset.get().getDatasetUrls().isEmpty()) {

			final String url = dataset.get().getDatasetUrls().stream()
					.filter(fileUrl -> fileUrl.endsWith(filename))
					.findFirst()
					.orElse(null);

			if (url == null) {
				final String error = "The file " + filename + " was not found in the dataset";
				log.error(error);
				throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, error);
			}

			final PresignedURL presigned = new PresignedURL().setUrl(url).setMethod("GET");
			return ResponseEntity.ok(presigned);

		} else {
			try {
				final Optional<PresignedURL> url = datasetService.getDownloadUrl(id, filename);
				return url.map(ResponseEntity::ok)
						.orElseGet(() -> ResponseEntity.notFound().build());
			} catch (final Exception e) {
				final String error = "Unable to get download url";
				log.error(error, e);
				throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
			}
		}
	}

	/**
	 * Downloads a CSV file from github given the path and owner name, then uploads
	 * it to the dataset.
	 */
	@PutMapping("/{id}/upload-csv-from-github")
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a CSV file from github to a dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Uploaded the CSV file.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the CSV", content = @Content)
	})
	public ResponseEntity<ResponseStatus> uploadCsvFromGithub(
			@PathVariable("id") final UUID datasetId,
			@RequestParam("path") final String path,
			@RequestParam("repo-owner-and-name") final String repoOwnerAndName,
			@RequestParam("filename") final String filename) {

		log.debug("Uploading CSV file from github to dataset {}", datasetId);

		// download CSV from github
		final String csvString = githubProxy.getGithubCode(repoOwnerAndName, path).getBody();

		if (csvString == null) {
			final String error = "Unable to download csv from github";
			log.error(error);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}

		final HttpEntity csvEntity = new StringEntity(csvString, ContentType.APPLICATION_OCTET_STREAM);
		final String[] csvRows = csvString.split("\\R");
		final String[] headers = csvRows[0].split(",");
		return uploadCSVAndUpdateColumns(datasetId, filename, csvEntity, headers);
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
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Uploaded the CSV file.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the CSV", content = @Content)
	})
	public ResponseEntity<ResponseStatus> uploadCsv(
			@PathVariable("id") final UUID datasetId,
			@RequestParam("filename") final String filename,
			@RequestPart("file") final MultipartFile input) {

		try {
			log.debug("Uploading CSV file to dataset {}", datasetId);

			final byte[] csvBytes = input.getBytes();

			final HttpEntity csvEntity = new ByteArrayEntity(csvBytes, ContentType.APPLICATION_OCTET_STREAM);
			final String csvString = new String(csvBytes);
			final String[] csvRows = csvString.split("\\R");
			final String[] headers = csvRows[0].split(",");
			return uploadCSVAndUpdateColumns(datasetId, filename, csvEntity, headers);
		} catch (final IOException e) {
			final String error = "Unable to upload csv dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping(value = "/{id}/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	@Operation(summary = "Uploads an arbitrary file to a dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Uploaded the file.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the file", content = @Content)
	})
	public ResponseEntity<Void> uploadData( // HttpServletRequest request,
			@PathVariable("id") final UUID datasetId,
			@RequestParam("filename") final String filename,
			@RequestPart("file") final MultipartFile input) {

		try {
			log.debug("Uploading file to dataset {}", datasetId);

			final ResponseEntity<Void> res = datasetService.getUploadStream(datasetId, filename, input);
			if (res.getStatusCode() == HttpStatus.OK) {
				// add the filename to existing file names
				final Optional<Dataset> updatedDataset = datasetService.getAsset(datasetId);
				if (updatedDataset.isEmpty()) {
					final String error = "Failed to get dataset after upload";
					log.error(error);
					throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
				}

				if (updatedDataset.get().getFileNames() == null) {
					updatedDataset.get().setFileNames(new ArrayList<>(List.of(filename)));
				} else {
					updatedDataset.get().getFileNames().add(filename);
				}

				datasetService.updateAsset(updatedDataset.get());
			}

			return res;
		} catch (final IOException e) {
			final String error = "Unable to upload file to dataset";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/upload-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to upload the dataset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Presigned url generated.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PresignedURL.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the presigned url", content = @Content)
	})
	public ResponseEntity<PresignedURL> getUploadURL(
			@PathVariable("id") final UUID id, @RequestParam("filename") final String filename) {
		try {
			return ResponseEntity.ok(datasetService.getUploadUrl(id, filename));
		} catch (final Exception e) {
			final String error = "Unable to get upload url";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
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
			final UUID datasetId, final String filename, final HttpEntity csvEntity, final String[] headers) {

		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {

			// upload CSV to S3
			final PresignedURL presignedURL = datasetService.getUploadUrl(datasetId, filename);
			final HttpPut put = new HttpPut(presignedURL.getUrl());
			put.setEntity(csvEntity);
			final HttpResponse response = httpclient.execute(put);
			final int status = response.getStatusLine().getStatusCode();

			// update dataset with headers if the previous upload was successful
			if (status == HttpStatus.OK.value()) {
				log.debug("Successfully uploaded CSV file to dataset {}. Now updating TDS with headers", datasetId);

				final Optional<Dataset> updatedDataset = datasetService.getAsset(datasetId);
				if (updatedDataset.isEmpty()) {
					log.error("Failed to get dataset {} after upload", datasetId);
					return ResponseEntity.internalServerError().build();
				}

				updateHeaders(updatedDataset.get(), Arrays.asList(headers));

				// add the filename to existing file names
				if (updatedDataset.get().getFileNames() == null) {
					updatedDataset.get().setFileNames(new ArrayList<>(List.of(filename)));
				} else {
					updatedDataset.get().getFileNames().add(filename);
				}

				datasetService.updateAsset(updatedDataset.get());
			}

			return ResponseEntity.ok(new ResponseStatus(status));

		} catch (final Exception e) {
			log.error("Unable to PUT csv data", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to PUT csv data");
		}
	}

	private static void updateHeaders(final Dataset dataset, final List<String> headers) {
		if (dataset.getColumns() == null) {
			dataset.setColumns(new ArrayList<>());
		}
		for (final String header : headers) {
			final DatasetColumn column = new DatasetColumn().setName(header).setAnnotations(new ArrayList<>());
			dataset.getColumns().add(column);
		}
	}

	private static List<List<String>> csvToRecords(final String rawCsvString) throws IOException {
		final List<List<String>> records = new ArrayList<>();
		try (final CSVParser parser = new CSVParser(new StringReader(rawCsvString), CSVFormat.DEFAULT)) {
			for (final CSVRecord csvRecord : parser) {
				final List<String> values = new ArrayList<>();
				csvRecord.forEach(values::add);
				records.add(values);
			}
		}
		return records;
	}

	private static List<String> getColumn(final List<List<String>> matrix, final int columnNumber) {
		final List<String> column = new ArrayList<>();
		for (final List<String> strings : matrix) {
			if (strings.size() > columnNumber) {
				column.add(strings.get(columnNumber));
			}
		}
		return column;
	}

	/**
	 * Given a column and an amount of bins creates a CsvColumnStats object.
	 *
	 * @param aCol column to get stats for
	 * @return CsvColumnStats object
	 */
	private static CsvColumnStats getStats(final List<String> aCol) {
		final List<Integer> bins = new ArrayList<>();
		try {
			// set up row as numbers. may fail here.
			// List<Integer> numberList = aCol.stream().map(String s ->
			// Integer.parseInt(s.trim()));
			final List<Double> numberList = aCol.stream().map(Double::valueOf).collect(Collectors.toList());
			Collections.sort(numberList);
			final double minValue = numberList.get(0);
			final double maxValue = numberList.get(numberList.size() - 1);
			final double meanValue = Stats.meanOf(numberList);
			final double medianValue = Quantiles.median().compute(numberList);
			final double sdValue = Stats.of(numberList).populationStandardDeviation();
			final int binCount = 10;
			// Set up bins
			for (int i = 0; i < binCount; i++) {
				bins.add(0);
			}
			final double stepSize = (numberList.get(numberList.size() - 1) - numberList.get(0)) / (binCount - 1);

			// Fill bins:
			for (final Double aDouble : numberList) {
				final int index = (int) Math.abs(Math.floor((aDouble - numberList.get(0)) / stepSize));
				final Integer value = bins.get(index);
				bins.set(index, value + 1);
			}

			return new CsvColumnStats(bins, minValue, maxValue, meanValue, medianValue, sdValue);

		} catch (final Exception e) {
			// Cannot convert column to double, just return empty list.
			return new CsvColumnStats(bins, 0, 0, 0, 0, 0);
		}
	}

	@GetMapping("/{id}/preview")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a preview of the data asset")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Dataset preview.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = JsonNode.class))),
			@ApiResponse(responseCode = "415", description = "Dataset cannot be previewed", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue generating the preview", content = @Content)
	})
	public ResponseEntity<JsonNode> getPreview(
			@PathVariable("id") final UUID id, @RequestParam("filename") final String filename) {

		// Currently `climate-data` service can only work on NetCDF files it knows about
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();

		// try {
		// if (filename.endsWith(".nc")) {
		// return climateDataProxy.previewEsgf(id.toString(), null, null, null);
		// } else {
		// final Optional<PresignedURL> url = datasetService.getDownloadUrl(id,
		// filename);
		// // TODO: This attempts to check the file, but fails to open the file, might
		// need
		// // to write a NetcdfFiles Stream reader
		// try (final NetcdfFile ncFile = NetcdfFiles.open(url.get().getUrl())) {
		// final ImmutableList<Attribute> globalAttributes =
		// ncFile.getGlobalAttributes();
		// for (final Attribute attribute : globalAttributes) {
		// final String name = attribute.getName();
		// final Array values = attribute.getValues();
		// // log.info("[{},{}]", name, values);
		// }
		// return climateDataProxy.previewEsgf(id.toString(), null, null, null);
		// } catch (final IOException ioe) {
		// throw new ResponseStatusException(
		// org.springframework.http.HttpStatus.valueOf(415),
		// "Unable to open file");
		// }
		// }
		// } catch (final Exception e) {
		// final String error = "Unable to get download url";
		// log.error(error, e);
		// throw new ResponseStatusException(
		// org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
		// error);
		// }
	}
}
