package software.uncharted.terarium.hmiserver.service.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.observation.annotation.Observed;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.models.TerariumAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.repository.PSCrudSoftDeleteRepository;
import software.uncharted.terarium.hmiserver.service.s3.S3ClientService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

/**
 * Base class for services that manage TerariumAssets without syncing to
 * Elasticsearch.
 *
 * @param <T> The type of asset this service manages
 * @param <R> The respository of the asset this service manages
 */
@Service
@Data
@RequiredArgsConstructor
@Slf4j
public abstract class TerariumAssetServiceWithoutSearch<T extends TerariumAsset, R extends PSCrudSoftDeleteRepository<T, UUID>>
		implements ITerariumAssetService<T> {

	protected final ObjectMapper objectMapper;

	/** The configuration for the application */
	protected final Config config;

	protected final ProjectAssetService projectAssetService;

	/** The repository for the asset this service manages */
	protected final R repository;

	/** The S3 client service */
	protected final S3ClientService s3ClientService;

	/** The class of the asset this service manages */
	protected final Class<T> assetClass;

	/** The expiration time for the presigned URLs in minutes */
	private static final long EXPIRATION = 60;

	/**
	 * Get an asset by its ID
	 *
	 * @param id The ID of the asset to get
	 * @return The asset, if it exists
	 */
	@Override
	@Observed(name = "function_profile")
	public Optional<T> getAsset(final UUID id, final Schema.Permission hasReadPermission) {
		return repository.getByIdAndDeletedOnIsNull(id);
	}

	/**
	 * Check if an asset exists by its ID
	 *
	 * @param id
	 * @return
	 */
	@Observed(name = "function_profile")
	public boolean assetExists(final UUID id) {
		return repository.getByIdAndDeletedOnIsNull(id).isPresent();
	}

	/**
	 * Get a list of assets, this includes all assets, not just searchable ones.
	 *
	 * @param page     The page number
	 * @param pageSize The number of assets per page
	 * @return The list of assets
	 */
	@Override
	@Observed(name = "function_profile")
	public List<T> getPublicNotTemporaryAssets(final Integer page, final Integer pageSize) {
		final Pageable pageable = PageRequest.of(page, pageSize);
		return repository
				.findAllByPublicAssetIsTrueAndTemporaryIsFalseAndDeletedOnIsNull(pageable)
				.getContent();
	}

	/**
	 * Delete an asset by its ID
	 *
	 * @param id The ID of the asset to delete
	 * @throws IOException If there is an error deleting the asset
	 */
	@Override
	@Observed(name = "function_profile")
	public Optional<T> deleteAsset(final UUID id, final Schema.Permission hasWritePermission) throws IOException {
		final Optional<T> asset = getAsset(id, hasWritePermission);
		if (asset.isEmpty()) {
			return Optional.empty();
		}
		asset.get().setDeletedOn(Timestamp.from(Instant.now()));
		repository.save(asset.get());
		return asset;
	}

	/**
	 * Create a new asset.
	 *
	 * @param asset The asset to create
	 * @return The created asset
	 * @throws IOException If there is an error creating the asset
	 */
	@Override
	@Observed(name = "function_profile")
	public T createAsset(final T asset, final Schema.Permission hasWritePermission) throws IOException {
		if (assetExists(asset.getId())) {
			throw new IllegalArgumentException("Asset already exists for id:" + asset.getId());
		}
		return repository.save(asset);
	}

	/**
	 * Create new assets.
	 *
	 * @param assets The assets to create
	 * @return The created asset
	 * @throws IOException If there is an error creating the asset
	 */
	@Override
	@Observed(name = "function_profile")
	public List<T> createAssets(final List<T> assets, final Schema.Permission hasWritePermission) throws IOException {
		final List<UUID> ids = assets.stream().map(TerariumAsset::getId).toList();
		final List<T> existing = repository.findAllByIdInAndDeletedOnIsNull(ids);
		if (existing.size() > 0) {
			throw new IllegalArgumentException("Asset already exists for id:"
					+ ids.stream().map(UUID::toString).toList());
		}
		return repository.saveAll(assets);
	}

	/**
	 * Update an asset.
	 *
	 * @param asset The asset to update
	 * @return The updated asset
	 * @throws IOException              If there is an error updating the asset
	 * @throws IllegalArgumentException If the asset tries to move from permanent to
	 *                                  temporary
	 * @throws NotFoundException        If the original asset does not exist
	 */
	@Override
	@Observed(name = "function_profile")
	public Optional<T> updateAsset(final T asset, final Schema.Permission hasWritePermission)
			throws IOException, IllegalArgumentException {

		final Optional<T> oldAsset = getAsset(asset.getId(), hasWritePermission);

		if (oldAsset.isEmpty()) {
			throw new NotFoundException(
					"Asset not found for id: " + asset.getId().toString());
		}

		if (asset.getTemporary() && !oldAsset.get().getTemporary()) {
			throw new IllegalArgumentException("Cannot update a non-temporary asset to be temporary");
		}

		final T updated = repository.save(asset);

		// Update the related ProjectAsset
		projectAssetService.updateByAsset(updated, hasWritePermission);

		return Optional.of(updated);
	}

	/** Clone asset and return it, does not persist it. */
	@Override
	@Observed(name = "function_profile")
	@SuppressWarnings("unchecked")
	public T cloneAsset(final UUID id, final Schema.Permission hasWritePermission)
			throws IOException, IllegalArgumentException {
		final Optional<T> targetAsset = getAsset(id, hasWritePermission);
		if (targetAsset.isEmpty()) {
			throw new IllegalArgumentException("Cannot clone non-existent asset: " + id.toString());
		}
		return (T) targetAsset.get().clone();
	}

	/** Clone asset, write it to the db under a new id, and return it. */
	@Observed(name = "function_profile")
	public T cloneAndPersistAsset(final UUID id, final Schema.Permission hasWritePermission)
			throws IOException, IllegalArgumentException {
		return createAsset(cloneAsset(id, hasWritePermission), hasWritePermission);
	}

	/** Returns the asset as a byte payload. */
	@Observed(name = "function_profile")
	public byte[] exportAsset(final UUID id, final Schema.Permission hasReadPermission) {
		try {
			return objectMapper.writeValueAsBytes(cloneAsset(id, hasReadPermission));
		} catch (final Exception e) {
			throw new RuntimeException("Failed to export asset", e);
		}
	}

	/** Imports the asset from a byte payload. */
	@Observed(name = "function_profile")
	public T importAsset(final byte[] bytes, final Schema.Permission hasWritePermission) {
		try {
			final T asset = objectMapper.readValue(bytes, assetClass);
			if (assetExists(asset.getId())) {
				throw new RuntimeException("Asset already exists for id:" + asset.getId());
			}
			return createAsset(asset, hasWritePermission);
		} catch (final Exception e) {
			throw new RuntimeException("Failed to export asset", e);
		}
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
	public Integer uploadFile(
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
			return response.getStatusLine().getStatusCode();
		}
	}

	@Observed(name = "function_profile")
	public Integer uploadFile(
			final UUID uuid, final String filename, final HttpEntity fileEntity)
			throws IOException {

		return uploadFile(uuid, filename, fileEntity, ContentType.parse(fileEntity.getContentType().getValue()));
	}

	private String getPath(final UUID id, final String filename) {
		return String.join("/", getAssetPath(), id.toString(), filename);
	}
}
