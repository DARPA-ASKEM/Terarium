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
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.code.Code;
import software.uncharted.terarium.hmiserver.proxies.dataservice.CodeProxy;
import software.uncharted.terarium.hmiserver.proxies.jsdelivr.JsDelivrProxy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequestMapping("/code-asset")
@RestController
@Slf4j
public class CodeResource implements SnakeCaseResource {

	@Autowired
	CodeProxy codeProxy;

	@Autowired
	JsDelivrProxy gitHubProxy;


	@GetMapping
	public ResponseEntity<List<Code>> getCodes(
		@RequestParam(name = "page_size", defaultValue = "100", required = false) final Integer pageSize,
		@RequestParam(name = "page", defaultValue = "0", required = false) final Integer page
	) {
		return ResponseEntity.ok(codeProxy.getAssets(pageSize, page).getBody());
	}

	@PostMapping
	public ResponseEntity<JsonNode> createCode(@RequestBody Code code) {

		return ResponseEntity.ok(codeProxy.createAsset(convertObjectToSnakeCaseJsonNode(code)).getBody());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Code> getCode(@PathVariable("id") String codeId) {

		return ResponseEntity.ok(codeProxy.getAsset(codeId).getBody());
	}

	@PutMapping("/{id}")
	public ResponseEntity<JsonNode> updateCode(@PathVariable("id") String codeId, @RequestBody Code code) {
		return ResponseEntity.ok(codeProxy.updateAsset(codeId, convertObjectToSnakeCaseJsonNode(code)).getBody());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<JsonNode> deleteCode(@PathVariable("id") String codeId) {
		return ResponseEntity.ok(codeProxy.deleteAsset(codeId).getBody());
	}

	@GetMapping("/{id}/download-code-as-text")
	public ResponseEntity<String> getCodeFileAsText(@PathVariable("id") String codeId, @RequestParam("filename") String filename) {

		log.debug("Downloading code file {} for code {}", filename, codeId);

		try (CloseableHttpClient httpclient = HttpClients.custom()
			.disableRedirectHandling()
			.build()) {

			PresignedURL presignedURL = codeProxy.getDownloadUrl(codeId, filename).getBody();
			final HttpGet httpGet = new HttpGet(presignedURL.getUrl());
			final HttpResponse response = httpclient.execute(httpGet);
			final String textFileAsString = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

			return ResponseEntity.ok(textFileAsString);


		} catch (Exception e) {
			log.error("Unable to GET code data", e);
			return ResponseEntity.internalServerError().build();
		}

	}

	@PutMapping(value = "/{codeId}/uploadFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Integer> uploadFile(
		@PathVariable("codeId") final String codeId,
		@RequestParam("filename") final String filename,
		@RequestPart("file") MultipartFile input
	) throws IOException {

		log.debug("Uploading code {} to project", codeId);

		byte[] fileAsBytes = input.getBytes();
		HttpEntity fileEntity = new ByteArrayEntity(fileAsBytes, ContentType.APPLICATION_OCTET_STREAM);
		return uploadCodeHelper(codeId, filename, fileEntity);


	}

	/**
	 * Downloads a file from GitHub given the path and owner name, then uploads it to the project.
	 */
	@PutMapping("/{codeId}/uploadCodeFromGithub")
	public ResponseEntity<Integer> uploadCodeFromGithub(
		@PathVariable("codeId") final String codeId,
		@RequestParam("path") final String path,
		@RequestParam("repoOwnerAndName") final String repoOwnerAndName,
		@RequestParam("filename") final String filename
	) {
		log.debug("Uploading code file from github to dataset {}", codeId);

		//download file from GitHub
		String fileString = gitHubProxy.getGithubCode(repoOwnerAndName, path).getBody();
		HttpEntity fileEntity = new StringEntity(fileString, ContentType.TEXT_PLAIN);
		return uploadCodeHelper(codeId, filename, fileEntity);

	}

	/**
	 * Uploads an code inside the entity to TDS via a presigned URL
	 *
	 * @param codeId         The ID of the code to upload to
	 * @param fileName       The name of the file to upload
	 * @param codeHttpEntity The entity containing the code to upload
	 * @return A response containing the status of the upload
	 */
	private ResponseEntity<Integer> uploadCodeHelper(String codeId, String fileName, HttpEntity codeHttpEntity) {

		try (CloseableHttpClient httpclient = HttpClients.custom()
			.disableRedirectHandling()
			.build()) {

			// upload file to S3
			final PresignedURL presignedURL = codeProxy.getUploadUrl(codeId, fileName).getBody();
			final HttpPut put = new HttpPut(presignedURL.getUrl());
			put.setEntity(codeHttpEntity);
			final HttpResponse response = httpclient.execute(put);


			return ResponseEntity.ok(response.getStatusLine().getStatusCode());


		} catch (Exception e) {
			log.error("Unable to PUT code data", e);
			return ResponseEntity.internalServerError().build();
		}
	}

}
