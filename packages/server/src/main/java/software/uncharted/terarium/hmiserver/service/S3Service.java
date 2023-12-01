package software.uncharted.terarium.hmiserver.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.uncharted.terarium.hmiserver.models.s3.S3ObjectListing;
import software.uncharted.terarium.hmiserver.models.s3.S3Object;


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
@Slf4j
public class S3Service {
	private final S3Client client;
	private final int BUFFER_SIZE;

	/**
	 * Constructor
	 * @param client      The S3 client to use
	 * @param bufferSize  The size of the buffer to use when writing multipart files
	 */
	public S3Service(S3Client client, int bufferSize) {
		if (client == null) {
			throw new IllegalArgumentException("client cannot be null");
		}
		this.client = client;
		this.BUFFER_SIZE = bufferSize;
	}

	public void destroy() {
		client.close();
	}

	/**
	 * List buckets
	 * @return  The list of buckets
	 */
	public List<String> listBuckets() {
		log.debug("Listing buckets");
		final ListBucketsResponse response = client.listBuckets();
		return response.buckets().stream().map(Bucket::name).toList();
	}

	/**
	 * Check if a bucket exists
	 * @param bucketName  The name of the bucket to check
	 * @return            True if the bucket exists, false otherwise
	 */
	public boolean bucketExists(final String bucketName) {
		log.debug("Checking if bucket {} exists", bucketName);
		final HeadBucketRequest request = HeadBucketRequest.builder()
			.bucket(bucketName)
			.build();
		try {
			client.headBucket(request);
			log.debug("Bucket {} exists", bucketName);
			return true;
		} catch (NoSuchBucketException e) {
			log.debug("Bucket {} does not exist", bucketName);
			return false;
		}
	}

	/**
	 * Create a bucket
	 * @param bucketName  The name of the bucket to create
	 * @return            The response from the create bucket request
	 */
	public CreateBucketResponse createBucket(final String bucketName) {
		log.debug("Creating bucket {}", bucketName);
		final CreateBucketRequest request = CreateBucketRequest.builder()
			.bucket(bucketName)
			.build();
		return client.createBucket(request);
	}

	/**
	 * Delete a bucket by name
	 * @param bucketName  The name of the bucket to delete
	 * @return            The response from the delete bucket request
	 */
	public DeleteBucketResponse deleteBucket(final String bucketName) {
		log.debug("Deleting bucket {}", bucketName);
		final DeleteBucketRequest request = DeleteBucketRequest.builder()
			.bucket(bucketName)
			.build();
		return client.deleteBucket(request);
	}

	/**
	 * Returns object metadata for an object in a bucket
	 * @param bucketName  The name of the bucket
	 * @param key         The key of the object
	 * @return            The {@link S3Object} metadata for the object, or null if the object does not exist
	 */
	public S3Object getObjectInformation(final String bucketName, final String key) {
		log.debug("Getting object {} from bucket {}", key, bucketName);
		final HeadObjectRequest request = HeadObjectRequest.builder()
			.bucket(bucketName)
			.key(key)
			.build();
		try {
			final HeadObjectResponse response = client.headObject(request);
			return new S3Object()
				.setKey(key)
				.setLastModifiedMillis(response.lastModified().toEpochMilli())
				.setETag(response.eTag())
				.setSizeInBytes(response.contentLength());
		} catch (NoSuchKeyException e) {
			log.debug("Object {} does not exist in bucket {}", key, bucketName);
			return null;
		}
	}

	/**
	 * Put an object in a bucket
	 * @param bucketName  The name of the bucket
	 * @param key         The key of the object
	 * @param data        The data to put in the object as a byte array
	 * @return            The response from the put object request
	 */
	public PutObjectResponse putObject(final String bucketName, final String key, final byte[] data) {
		log.debug("Putting object {} in bucket {}", key, bucketName);
		final PutObjectRequest request = PutObjectRequest.builder()
			.bucket(bucketName)
			.key(key)
			.build();
		return client.putObject(request, RequestBody.fromBytes(data));
	}

	/**
	 * Initialize a multipart upload. The caller can use the returned upload ID to upload parts and to complete the multipart upload.
	 * @param bucketName  The name of the bucket
	 * @param key         The key of the object
	 * @return            The response from the create multipart upload request
	 */
	public CreateMultipartUploadResponse createMultipartUpload(final String bucketName, final String key) {
		log.debug("Creating multipart upload for object {} in bucket {}", key, bucketName);
		final CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
			.bucket(bucketName)
			.key(key)
			.build();
		return client.createMultipartUpload(request);
	}

	/**
	 * Put an object in a bucket via an input stream and content length.  Useful for upload multipart files that are too large to fit in memory.
	 * @param uploadId        The upload ID of the multipart upload.
	 * @param bucketName      The name of the bucket
	 * @param key             The key of the object
	 * @param stream          The input stream to read from
	 */
	public void putObject(final String uploadId, final String bucketName, final String key, final InputStream stream) {
		log.debug("Putting multipart object {} in bucket {}", key, bucketName);
		try {
			int partNumber = 1;
			final List<CompletedPart> completedParts = new ArrayList<>();
			final List<byte[]> buffers = new ArrayList<>();
			long totalBytesWritten = 0L;
			try (BufferedInputStream bufferedStream = new BufferedInputStream(stream, BUFFER_SIZE)) {
				int bytesRead;
				byte[] buffer = new byte[BUFFER_SIZE];
				int totalBuffersSize = 0;
				while ((bytesRead = stream.read(buffer)) != -1) {
					// Copy the buffer to a new byte array of the correct size and add it to the list
					final byte[] bufferCopy = new byte[bytesRead];
					System.arraycopy(buffer, 0, bufferCopy, 0, bytesRead);
					buffers.add(bufferCopy);
					totalBuffersSize += bytesRead;
					if (totalBuffersSize > BUFFER_SIZE) {
						completedParts.add(writePart(totalBuffersSize, buffers, bucketName, uploadId, key, partNumber));
						log.info("Total bytes written: " + FileUtils.byteCountToDisplaySize(totalBytesWritten));
						partNumber++;
						buffers.clear();
						totalBytesWritten += totalBuffersSize;
						totalBuffersSize = 0;
					}
				}

				if (!buffers.isEmpty()) {
					completedParts.add(writePart(totalBuffersSize, buffers, bucketName, uploadId, key, partNumber));
					log.info("Total bytes written: " + FileUtils.byteCountToDisplaySize(totalBytesWritten));
				}
			}

			client.completeMultipartUpload(b -> b
				.bucket(bucketName)
				.key(key)
				.uploadId(uploadId)
				.multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));
		} catch (IOException e) {
			log.error("Error putting multipart object {} in bucket {}", key, bucketName, e);
		}
	}

	/**
	 * Write a part of a multipart upload
	 * @param totalBuffersSize  The total size of the buffers in bytes
	 * @param buffers           The buffers to write
	 * @param bucketName        The name of the bucket
	 * @param uploadId          The upload ID of the multipart upload
	 * @param key               The key of the object
	 * @param partNumber        The part number of the part to write
	 * @return                  The completed part
	 */
	private CompletedPart writePart(final int totalBuffersSize, final List<byte[]> buffers, final String bucketName, final String uploadId, final String key, final int partNumber) {
		final byte[] newBuffer = new byte[totalBuffersSize];
		int offset = 0;
		for (byte[] b : buffers) {
			System.arraycopy(b, 0, newBuffer, offset, b.length);
			offset += b.length;
		}

		final UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
			.bucket(bucketName)
			.key(key)
			.uploadId(uploadId)
			.partNumber(partNumber)
			.build();

		final UploadPartResponse uploadPartResponse = client.uploadPart(uploadPartRequest, RequestBody.fromBytes(newBuffer));

		final CompletedPart completedPart = CompletedPart.builder()
			.partNumber(partNumber)
			.eTag(uploadPartResponse.eTag())
			.build();

		return completedPart;
	}

	/**
	 * Put an object in a bucket
	 * @param bucketName  The name of the bucket
	 * @param key         The key of the object
	 * @return            The response from the put object request
	 */
	public ResponseInputStream<GetObjectResponse> getObject(final String bucketName, final String key) {
		log.debug("Getting object {} from bucket {}", key, bucketName);
		final GetObjectRequest request = GetObjectRequest.builder()
			.bucket(bucketName)
			.key(key)
			.build();
		return client.getObject(request);
	}

	/**
	 * Tests if an object exists in a bucket
	 * @param bucketName  The name of the bucket
	 * @param key         The key of the object
	 * @return            True if the object exists, false otherwise
	 */
	public boolean objectExists(final String bucketName, final String key) {
		log.debug("Checking if object {} exists in bucket {}", key, bucketName);
		final HeadObjectRequest request = HeadObjectRequest.builder()
			.bucket(bucketName)
			.key(key)
			.build();
		try {
			client.headObject(request);
			log.debug("Object {} exists in bucket {}", key, bucketName);
			return true;
		} catch (NoSuchKeyException e) {
			log.debug("Object {} does not exist in bucket {}", key, bucketName);
			return false;
		}
	}

	/**
	 * Delete an object from a bucket
	 * @param bucketName  The name of the bucket
	 * @param key         The key of the object
	 * @return            The response from the delete object request
	 */
	public DeleteObjectResponse deleteObject(final String bucketName, final String key) {
		log.debug("Deleting object {} from bucket {}", key, bucketName);
		final DeleteObjectRequest request = DeleteObjectRequest.builder()
			.bucket(bucketName)
			.key(key)
			.build();
		return client.deleteObject(request);
	}

	/**
	 * List objects in a bucket
	 * @param bucketName  The name of the bucket
	 * @param prefix      The prefix to filter on
	 * @return            The list of objects in the bucket
	 */
	public S3ObjectListing listObjects(final String bucketName, final String prefix) {
		log.debug("Listing objects in bucket {} with prefix {}", bucketName, prefix);
		final ListObjectsRequest request = ListObjectsRequest.builder()
			.bucket(bucketName)
			.prefix(prefix)
			.build();
		final ListObjectsResponse response = client.listObjects(request);
		final S3ObjectListing listing = new S3ObjectListing();
		listing
			.setTruncated(response.isTruncated())
			.getContents().addAll(
				response.contents().stream()
					.map(o -> new S3Object()
						.setKey(o.key())
						.setLastModifiedMillis(o.lastModified().toEpochMilli())
						.setETag(o.eTag())
						.setSizeInBytes(o.size())
					).toList()
			);
		return listing;
	}

	/**
	 * Get the pre-signed signature for a download
	 * @param bucket          The bucket to download from
	 * @param key             The key to download
	 * @param s3id            The ID of the S3 client to use
	 * @param encryptionKey   The encryption key to use
	 * @return                The signature of this request
	 */
	public static String getSignature(final String bucket, final String key, final String s3id, final String encryptionKey) {
		final String currentTimestampSeconds = String.valueOf(System.currentTimeMillis() / 1000);
		log.info("Valid timestamp {}", currentTimestampSeconds);
		return getSignature(bucket, key, s3id, currentTimestampSeconds, encryptionKey);
	}

	/**
	 * Gets the signature for a request and timestamp
	 * @param bucket                    The bucket to download from
	 * @param key                       The key to download
	 * @param s3Id                      The ID of the S3 client to use
	 * @param currentTimestampSeconds   The current timestamp in seconds
	 * @param encryptionKey             The encryption key to use
	 * @return                          The signature of this request
	 */
	public static String getSignature(final String bucket, final String key, final String s3Id, final String currentTimestampSeconds, final String encryptionKey) {
		return HashService.sha256(String.format("%s:%s:%s:%s:%s", bucket, key, s3Id, currentTimestampSeconds, encryptionKey));
	}

	/**
	 * Validates a signature for a download by checking if the signature matches any of the signatures that would be
	 * valid in the last expirationSeconds seconds
	 * @param bucket              The bucket to download from
	 * @param key                 The key to download
	 * @param s3Id                The ID of the S3 client to use
	 * @param signature           The signature to validate
	 * @param encryptionKey       The encryption key to use
	 * @param expirationSeconds   The number of seconds the signature is valid for
	 * @return                    True if the signature is valid, false otherwise
	 */
	public static Boolean validateSignature(final String bucket, final String key, final String s3Id, final String signature, final String encryptionKey, final int expirationSeconds) {
		final long rangeEnd = System.currentTimeMillis() / 1000;
		final long rangeStart = rangeEnd - expirationSeconds;
		return LongStream.range(rangeStart, rangeEnd + 1) // +1 because we want to include the current timestamp
			.mapToObj(String::valueOf)
			.map(timestamp -> getSignature(bucket, key, s3Id, timestamp, encryptionKey))
			.anyMatch(potentialSignature -> potentialSignature.equals(signature));
	}
}
