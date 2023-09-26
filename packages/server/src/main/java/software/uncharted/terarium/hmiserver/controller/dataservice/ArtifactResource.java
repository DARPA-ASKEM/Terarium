package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.uncharted.terarium.hmiserver.controller.SnakeCaseResource;
import software.uncharted.terarium.hmiserver.models.dataservice.Artifact;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ArtifactProxy;
import software.uncharted.terarium.hmiserver.proxies.jsdelivr.JsDelivrProxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


@RequestMapping("/artifacts")
@RestController
@Slf4j
public class ArtifactResource implements SnakeCaseResource {

	@Autowired
	ArtifactProxy artifactProxy;

	@Autowired
	JsDelivrProxy gitHubProxy;


	@GetMapping
	public ResponseEntity<List<Artifact>> getArtifacts(
			@RequestParam(name = "page_size", defaultValue = "100", required = false) final Integer pageSize,
			@RequestParam(name = "page", defaultValue = "0", required = false) final Integer page
	) {
		return ResponseEntity.ok(artifactProxy.getAssets(pageSize, page).getBody());
	}

	@PostMapping
	public ResponseEntity<JsonNode> createArtifact(@RequestBody  Artifact artifact) {
		return ResponseEntity.ok(artifactProxy.createAsset(convertObjectToSnakeCaseJsonNode(artifact)).getBody());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Artifact> getArtifact(@PathVariable("id") String artifactId) {
		return ResponseEntity.ok(artifactProxy.getAsset(artifactId).getBody());
	}

	@PutMapping("/{id}")
	public ResponseEntity<JsonNode> updateArtifact(@PathVariable("id") String artifactId, @RequestBody Artifact artifact) {
		return ResponseEntity.ok(artifactProxy.updateAsset(artifactId, convertObjectToSnakeCaseJsonNode(artifact)).getBody());
	}
	@DeleteMapping("/{id}")
	public ResponseEntity<JsonNode> deleteArtifact(@PathVariable("id") String artifactId) {
		return ResponseEntity.ok(artifactProxy.deleteAsset(artifactId).getBody());
	}

	@GetMapping("/{id}/download-file-as-text")
	public ResponseEntity<String> downloadFileAsText(@PathVariable("id") String artifactId, @RequestParam("filename") String filename) {

		try (CloseableHttpClient httpclient = HttpClients.custom()
			.disableRedirectHandling()
			.build()) {

			final PresignedURL presignedURL = artifactProxy.getDownloadUrl(artifactId, filename).getBody();
			final HttpGet get = new HttpGet(presignedURL.getUrl());
			final HttpResponse response = httpclient.execute(get);
			final String textFileAsString = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

			return ResponseEntity.ok(textFileAsString);

		} catch (Exception e) {
			log.error("Unable to GET file as string data", e);
			return ResponseEntity.internalServerError().build();
		}

	}

	@GetMapping("/{id}/download-file")
	public ResponseEntity<byte[]> downloadFile(@PathVariable("id") String artifactId, @RequestParam("filename") String filename) {

		log.debug("Downloading artifact {} from project", artifactId);


		try (CloseableHttpClient httpclient = HttpClients.custom()
				.disableRedirectHandling()
				.build()) {

			final PresignedURL presignedURL = artifactProxy.getDownloadUrl(artifactId, filename).getBody();
			final HttpGet get = new HttpGet(presignedURL.getUrl());
			final HttpResponse response = httpclient.execute(get);
			if(response.getStatusLine().getStatusCode() == 200 && response.getEntity() != null) {
				byte[] fileAsBytes = response.getEntity().getContent().readAllBytes();
				return ResponseEntity.ok(fileAsBytes);
			}
			return ResponseEntity.status(response.getStatusLine().getStatusCode()).build();

		} catch (Exception e) {
			log.error("Unable to GET artifact data", e);
			return ResponseEntity.internalServerError().build();
		}

	}

	@PutMapping(value = "/{artifactId}/uploadFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Integer> uploadFile(
		@PathVariable("artifactId") final String artifactId,
		@RequestParam("filename") final String filename,
		@RequestPart("file") MultipartFile input
	) throws IOException {

		log.debug("Uploading artifact {} to project", artifactId);

		byte[] fileAsBytes = input.getBytes();
		HttpEntity fileEntity = new ByteArrayEntity(fileAsBytes, ContentType.APPLICATION_OCTET_STREAM);
		return uploadArtifactHelper(artifactId, filename, fileEntity);


	}

	/**
	 * Downloads a file from GitHub given the path and owner name, then uploads it to the project.
	 */
	@PutMapping("/{artifactId}/uploadArtifactFromGithub")
	public ResponseEntity<Integer> uploadArtifactFromGithub(
		@PathVariable("artifactId") final String artifactId,
		@RequestParam("path") final String path,
		@RequestParam("repoOwnerAndName") final String repoOwnerAndName,
		@RequestParam("filename") final String filename
	){
		log.debug("Uploading artifact file from github to dataset {}", artifactId);

		//download file from GitHub
		String fileString = gitHubProxy.getGithubCode(repoOwnerAndName, path).getBody();
		HttpEntity fileEntity = new StringEntity(fileString, ContentType.TEXT_PLAIN);
		return uploadArtifactHelper(artifactId, filename, fileEntity);

	}

	/**
	 * Uploads an artifact inside the entity to TDS via a presigned URL
	 * @param artifactId The ID of the artifact to upload to
	 * @param fileName The name of the file to upload
	 * @param artifactHttpEntity The entity containing the artifact to upload
	 * @return A response containing the status of the upload
	 */
	private ResponseEntity<Integer> uploadArtifactHelper(String artifactId, String fileName, HttpEntity artifactHttpEntity){

		try (CloseableHttpClient httpclient = HttpClients.custom()
			.disableRedirectHandling()
			.build()) {

			// upload file to S3
			final PresignedURL presignedURL = artifactProxy.getUploadUrl(artifactId, fileName).getBody();
			final HttpPut put = new HttpPut(presignedURL.getUrl());
			put.setEntity(artifactHttpEntity);
			final HttpResponse response = httpclient.execute(put);
;

			return ResponseEntity.ok(response.getStatusLine().getStatusCode());


		} catch (Exception e) {
			log.error("Unable to PUT artifact data", e);
			return ResponseEntity.internalServerError().build();
		}
	}






}
