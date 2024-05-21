package software.uncharted.terarium.hmiserver.controller.dataservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.code.Code;
import software.uncharted.terarium.hmiserver.models.dataservice.code.CodeFile;
import software.uncharted.terarium.hmiserver.proxies.github.GithubProxy;
import software.uncharted.terarium.hmiserver.proxies.jsdelivr.JsDelivrProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.data.CodeService;
import software.uncharted.terarium.hmiserver.service.data.ProjectAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/code-asset")
@RestController
@Slf4j
@RequiredArgsConstructor
public class TDSCodeController {

	final Messages messages;

	final JsDelivrProxy jsdelivrProxy;

	final GithubProxy githubProxy;

	final CodeService codeService;

	final ProjectService projectService;
	final ProjectAssetService projectAssetService;
	final CurrentUserService currentUserService;

	/**
	 * Retrieves a list of codes.
	 *
	 * @param pageSize The number of codes to retrieve per page (optional, default
	 *                 value is 100).
	 * @param page     The page number to retrieve (optional, default value is 0).
	 * @return A ResponseEntity containing a list of Code objects if successful, or
	 *         an empty list if no codes are found.
	 */
	@GetMapping
	@Secured(Roles.USER)
	@Operation(summary = "Gets all code resources")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "publications found.", content = @Content(array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Code.class)))),
			@ApiResponse(responseCode = "204", description = "There are no publications found and no errors occurred", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving publications from the data store", content = @Content)
	})
	public ResponseEntity<List<Code>> getCodes(
			@RequestParam(name = "page-size", defaultValue = "100", required = false) final Integer pageSize,
			@RequestParam(name = "page", defaultValue = "1", required = false) final Integer page) {
		try {
			return ResponseEntity.ok(codeService.getPublicNotTemporaryAssets(pageSize, page));
		} catch (final Exception e) {
			log.error("Unable to get code resources", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get code resources");
		}
	}

	/**
	 * Creates a code.
	 *
	 * @param code The code to be created.
	 * @return A ResponseEntity containing the created code's ID in JSON format.
	 */
	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new code resource")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Code resource created.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Code.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the code resource", content = @Content)
	})
	public ResponseEntity<Code> createCode(@RequestBody Code code, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			code = codeService.createAsset(code, permission);
			return ResponseEntity.status(HttpStatus.CREATED).body(code);
		} catch (final IOException e) {
			log.error("Unable to create code resource", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create code resource");
		}
	}

	/**
	 * Retrieves the code with the specified ID.
	 *
	 * @param id the ID of the code to be retrieved
	 * @return a ResponseEntity containing the code
	 * @throws ResponseStatusException if the code is not found with the specified
	 *                                 ID
	 */
	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets code resource by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Code resource found.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Code.class))),
			@ApiResponse(responseCode = "404", description = "There was no code resource found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the code resource from the data store", content = @Content)
	})
	public ResponseEntity<Code> getCode(
			@PathVariable("id") final UUID id, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			final Optional<Code> code = codeService.getAsset(id, permission);
			return code.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.noContent().build());
		} catch (final Exception e) {
			log.error("Unable to get code resource", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get code resource");
		}
	}

	/**
	 * Updates the code with the specified ID.
	 *
	 * @param codeId The ID of the code to update.
	 * @param code   The updated code information.
	 * @return The HTTP response entity containing a JSON node with the updated code
	 *         ID.
	 */
	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a code resource")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Code resource updated.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Code.class))),
			@ApiResponse(responseCode = "404", description = "Code resource could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the code resource", content = @Content)
	})
	public ResponseEntity<Code> updateCode(
			@PathVariable("id") final UUID codeId,
			@RequestBody final Code code,
			@RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			code.setId(codeId);
			final Optional<Code> updated = codeService.updateAsset(code, permission);
			return updated.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final NotFoundException e) {
			log.error("Unable to find code resource", e);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("code.not-found"));
		} catch (final IOException e) {
			log.error("Unable to update code resource", e);
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE, messages.get("postgres.service-unavailable"));
		}
	}

	/**
	 * Deletes a code with the given ID.
	 *
	 * @param id The ID of the code to delete.
	 * @return A ResponseEntity containing a JsonNode object with a success message.
	 */
	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Delete a code resource by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Code resource deleted.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue deleting the code resource", content = @Content)
	})
	public ResponseEntity<ResponseDeleted> deleteCode(
			@PathVariable("id") final UUID id, @RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try {
			codeService.deleteAsset(id, permission);
		} catch (final IOException e) {
			log.error("Unable to delete code resource", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to delete code resource");
		}

		return ResponseEntity.ok(new ResponseDeleted("Code", id));
	}

	/**
	 * Retrieves the content of a code file as text.
	 *
	 * @param codeId   the ID of the code file to be retrieved
	 * @param filename the name of the code file
	 * @return a ResponseEntity object containing the content of the code file as
	 *         text
	 */
	@GetMapping("/{id}/download-code-as-text")
	@Secured(Roles.USER)
	@Operation(summary = "Gets code file as text")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Code file found.", content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the code file from the data store", content = @Content)
	})
	public ResponseEntity<String> getCodeFileAsText(
			@PathVariable("id") final UUID codeId, @RequestParam("filename") final String filename) {

		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {

			final Optional<PresignedURL> url = codeService.getDownloadUrl(codeId, filename);
			if (url.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			final PresignedURL presignedURL = url.get();
			final HttpGet get = new HttpGet(presignedURL.getUrl());
			final HttpResponse response = httpclient.execute(get);
			final String textFileAsString = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

			return ResponseEntity.ok(textFileAsString);

		} catch (final Exception e) {
			log.error("Unable to GET file as string data", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get file as string data");
		}
	}

	@GetMapping("/{id}/download-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to download the code file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Presigned url generated.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PresignedURL.class))),
			@ApiResponse(responseCode = "404", description = "There was no code resource found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the presigned url", content = @Content)
	})
	public ResponseEntity<PresignedURL> getDownloadURL(
			@PathVariable("id") final UUID id, @RequestParam("filename") final String filename) {
		try {

			final Optional<PresignedURL> url = codeService.getDownloadUrl(id, filename);
			return url.map(ResponseEntity::ok)
					.orElseGet(() -> ResponseEntity.notFound().build());

		} catch (final Exception e) {
			final String error = "Unable to get download url";
			log.error(error, e);
			throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Uploads a file to the specified codeId.
	 *
	 * @param codeId   the code ID to upload the file to
	 * @param filename the name of the file to be uploaded
	 * @param input    the file to be uploaded
	 * @return a ResponseEntity object with an Integer indicating the result of the
	 *         upload
	 * @throws IOException if an I/O error occurs while reading the file
	 */
	@PutMapping(value = "/{id}/upload-code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a file to the specified codeId")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "File uploaded.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Integer.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the file", content = @Content)
	})
	public ResponseEntity<Integer> uploadFile(
			@PathVariable("id") final UUID codeId,
			@RequestParam("filename") final String filename,
			@RequestPart("file") final MultipartFile input,
			@RequestParam("project-id") final UUID projectId)
			throws IOException {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		log.debug("Uploading code {} to project", codeId);

		final byte[] fileAsBytes = input.getBytes();
		final HttpEntity fileEntity = new ByteArrayEntity(fileAsBytes, ContentType.APPLICATION_OCTET_STREAM);
		return uploadCodeHelper(codeId, filename, fileEntity, permission);
	}

	/**
	 * Downloads a file from GitHub given the path and owner name, then uploads it
	 * to the project.
	 */
	@PutMapping("/{id}/upload-code-from-github")
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a file from GitHub given the path and owner name, then uploads it to the project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "File uploaded.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Integer.class)))
	})
	public ResponseEntity<Integer> uploadCodeFromGithub(
			@PathVariable("id") final UUID codeId,
			@RequestParam("path") final String path,
			@RequestParam("repo-owner-and-name") final String repoOwnerAndName,
			@RequestParam("filename") final String filename,
			@RequestParam("project-id") final UUID projectId) {
		log.debug("Uploading code file from github to dataset {}", codeId);
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		// download file from GitHub
		final String fileString = jsdelivrProxy.getGithubCode(repoOwnerAndName, path).getBody();
		if (fileString == null) {
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get file as string data");
		}
		final HttpEntity fileEntity = new StringEntity(fileString, ContentType.TEXT_PLAIN);
		return uploadCodeHelper(codeId, filename, fileEntity, permission);
	}

	/**
	 * Downloads a file from GitHub given the path and owner name, then uploads it
	 * to the project.
	 *
	 * @param codeId           The ID of the code to upload to
	 * @param repoOwnerAndName The owner and name of the repo to upload from
	 * @param repoName         The name of the repo to upload from
	 * @return A response containing the status of the upload
	 */
	@PutMapping("/{id}/upload-code-from-github-repo")
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a file from GitHub given the path and owner name, then uploads it to the project")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "File uploaded.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Integer.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the file", content = @Content)
	})
	public ResponseEntity<Integer> uploadCodeFromGithubRepo(
			@PathVariable("id") final UUID codeId,
			@RequestParam("repo-owner-and-name") final String repoOwnerAndName,
			@RequestParam("repo-name") final String repoName,
			@RequestParam("project-id") final UUID projectId) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(currentUserService.get().getId(),
				projectId);

		try (final CloseableHttpClient httpClient = HttpClients.custom().build()) {

			final String githubApiUrl = "https://api.github.com/repos/" + repoOwnerAndName + "/zipball/";

			// get github repo zip
			final HttpGet httpGet = new HttpGet(githubApiUrl);
			final HttpResponse response = httpClient.execute(httpGet);
			final byte[] zipBytes = response.getEntity().getContent().readAllBytes();

			final HttpEntity fileEntity = new ByteArrayEntity(zipBytes, ContentType.APPLICATION_OCTET_STREAM);

			return uploadCodeHelper(codeId, repoName, fileEntity, permission);

		} catch (final Exception e) {
			log.error("Unable to GET file as string data", e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get file as string data");
		}
	}

	/**
	 * Uploads an code inside the entity to TDS via a presigned URL
	 *
	 * @param codeId         The ID of the code to upload to
	 * @param fileName       The name of the file to upload
	 * @param codeHttpEntity The entity containing the code to upload
	 * @return A response containing the status of the upload
	 */
	private ResponseEntity<Integer> uploadCodeHelper(
			final UUID codeId,
			final String fileName,
			final HttpEntity codeHttpEntity,
			final Schema.Permission hasWritePermission) {
		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {

			// upload file to S3
			final Integer status = codeService.uploadFile(codeId, fileName, codeHttpEntity);

			final Optional<Code> code = codeService.getAsset(codeId, hasWritePermission);
			if (code.isEmpty()) {
				throw new ResponseStatusException(
						org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get code");
			}
			final CodeFile codeFile = new CodeFile();
			codeFile.setFileNameAndProgrammingLanguage(fileName);

			Map<String, CodeFile> fileMap = code.get().getFiles();

			if (fileMap == null) {
				fileMap = new HashMap<>();
			}
			fileMap.put(fileName, codeFile);
			code.get().setFiles(fileMap);
			codeService.updateAsset(code.get(), hasWritePermission);

			return ResponseEntity.ok(status);

		} catch (final Exception e) {
			log.error("Unable to PUT artifact data", e);
			return ResponseEntity.internalServerError().build();
		}
	}
}
