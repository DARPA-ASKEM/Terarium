package software.uncharted.terarium.hmiserver.service.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.micrometer.observation.annotation.Observed;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;

/**
 * Base class for services that manage TerariumAssets that are backed by S3
 *
 * @param <T>
 */
public abstract class S3BackedAssetService<T extends TerariumAsset> extends TerariumAssetService<T> {
	/** The expiration time for the presigned URLs in minutes */
	private static final long EXPIRATION = 60;
	/** The S3 client service */
	private final S3ClientService s3ClientService;

	/**
	 * Create a new S3BackedAssetService
	 *
	 * @param elasticConfig   The configuration for the Elasticsearch service
	 * @param config          The configuration for the application
	 * @param elasticService  The Elasticsearch service
	 * @param s3ClientService The S3 client service
	 * @param assetClass      The class of the asset this service manages
	 */
	public S3BackedAssetService(
			final ElasticsearchConfiguration elasticConfig,
			final Config config,
			final ElasticsearchService elasticService,
			final ProjectAssetService projectAssetService,
			final S3ClientService s3ClientService,
			final Class<T> assetClass) {
		super(elasticConfig, config, elasticService, projectAssetService, assetClass);
		this.s3ClientService = s3ClientService;
	}

	/**
	 * Get the path to the asset in S3
	 *
	 * @return The path to the asset in S3
	 */
	protected abstract String getAssetPath();

	/**
	 * Get a presigned URL for uploading a file to S3
	 *
	 * @param id       The ID of the asset to upload to
	 * @param filename The name of the file to upload
	 * @return The presigned URL
	 */
	@Observed(name = "function_profile")
	private PresignedURL getUploadUrl(final UUID id, final String filename) {

		final PresignedURL presigned = new PresignedURL();
		presigned.setUrl(s3ClientService
				.getS3Service()
				.getS3PreSignedPutUrl(config.getFileStorageS3BucketName(), getPath(id, filename), EXPIRATION));
		presigned.setMethod("PUT");
		return presigned;
	}

	/**
	 * Get a presigned URL for downloading a file from S3
	 *
	 * @param id       The ID of the asset to download from
	 * @param filename The name of the file to download
	 * @return The presigned URL
	 */
	@Observed(name = "function_profile")
	public Optional<PresignedURL> getDownloadUrl(final UUID id, final String filename) {

		final Optional<String> url = s3ClientService
				.getS3Service()
				.getS3PreSignedGetUrl(config.getFileStorageS3BucketName(), getPath(id, filename), EXPIRATION);

		if (url.isEmpty()) {
			return Optional.empty();
		}

		final PresignedURL presigned = new PresignedURL();
		presigned.setUrl(url.get());
		presigned.setMethod("GET");
		return Optional.of(presigned);
	}

	@Observed(name = "function_profile")
	public ResponseEntity<Void> getUploadStream(final UUID uuid, final String filename, final MultipartFile file)
			throws IOException {
		final String bucket = config.getFileStorageS3BucketName();
		final String key = getPath(uuid, filename);

		return s3ClientService.getS3Service().getUploadStream(bucket, key, file);
	}

	@Observed(name = "function_profile")
	public ResponseEntity<StreamingResponseBody> getDownloadStream(final UUID uuid, final String filename) {
		final String bucket = config.getFileStorageS3BucketName();
		final String key = getPath(uuid, filename);

		return s3ClientService.getS3Service().getDownloadStream(bucket, key);
	}

	@Observed(name = "function_profile")
	public Optional<String> fetchFileAsString(final UUID uuid, final String filename) throws IOException {
		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {

			final Optional<PresignedURL> url = getDownloadUrl(uuid, filename);
			if (url.isEmpty()) {
				return Optional.empty();
			}
			final PresignedURL presignedURL = url.get();
			final HttpGet get = new HttpGet(Objects.requireNonNull(presignedURL).getUrl());
			final HttpResponse response = httpclient.execute(get);
			return Optional.of(IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
		}
	}

	@Observed(name = "function_profile")
	public Optional<byte[]> fetchFileAsBytes(final UUID uuid, final String filename) throws IOException {
		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {

			final Optional<PresignedURL> url = getDownloadUrl(uuid, filename);
			if (url.isEmpty()) {
				return Optional.empty();
			}
			final PresignedURL presignedURL = url.get();
			final HttpGet get = new HttpGet(Objects.requireNonNull(presignedURL).getUrl());
			final HttpResponse response = httpclient.execute(get);
			return Optional.of(IOUtils.toByteArray(response.getEntity().getContent()));
		}
	}

	@Observed(name = "function_profile")
	public void uploadFile(
			final UUID uuid, final String filename, final HttpEntity fileEntity, final ContentType contentType)
			throws IOException {
		try (final CloseableHttpClient httpclient = HttpClients.custom().disableRedirectHandling().build()) {

			final PresignedURL presignedURL = getUploadUrl(uuid, filename);
			final HttpPut put = new HttpPut(presignedURL.getUrl());
			put.setEntity(fileEntity);
			put.setHeader("Content-Type", contentType.toString());
			final HttpResponse response = httpclient.execute(put);
			if (response.getStatusLine().getStatusCode() >= 300) {
				throw new IOException("Failed to upload file to S3: "
						+ response.getStatusLine().getReasonPhrase());
			}
		}
	}

	private String getPath(final UUID id, final String filename) {
		return String.join("/", getAssetPath(), id.toString(), filename);
	}
}
