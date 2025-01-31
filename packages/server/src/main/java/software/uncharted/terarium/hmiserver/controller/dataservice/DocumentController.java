package software.uncharted.terarium.hmiserver.controller.dataservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
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
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.controller.services.DownloadService;
import software.uncharted.terarium.hmiserver.models.dataservice.PresignedURL;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseStatus;
import software.uncharted.terarium.hmiserver.models.dataservice.document.DocumentAsset;
import software.uncharted.terarium.hmiserver.proxies.jsdelivr.JsDelivrProxy;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaRustProxy;
import software.uncharted.terarium.hmiserver.proxies.skema.SkemaUnifiedProxy;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.ExtractionService;
import software.uncharted.terarium.hmiserver.service.data.DocumentAssetService;
import software.uncharted.terarium.hmiserver.service.data.ProjectService;
import software.uncharted.terarium.hmiserver.service.gollm.EmbeddingService;
import software.uncharted.terarium.hmiserver.utils.Messages;
import software.uncharted.terarium.hmiserver.utils.rebac.ReBACService;
import software.uncharted.terarium.hmiserver.utils.rebac.Schema;

@RequestMapping("/document-asset")
@RestController
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

	final Config config;
	final ReBACService reBACService;
	final CurrentUserService currentUserService;
	final Messages messages;

	final SkemaUnifiedProxy skemaUnifiedProxy;

	final SkemaRustProxy skemaRustProxy;

	final JsDelivrProxy gitHubProxy;

	final DownloadService downloadService;

	private final ProjectService projectService;

	final DocumentAssetService documentAssetService;

	final ObjectMapper objectMapper;
	final ExtractionService extractionService;
	final EmbeddingService embeddingService;

	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new document")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "201",
				description = "Document created.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentAsset.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the document", content = @Content)
		}
	)
	public ResponseEntity<DocumentAsset> createDocument(
		@RequestBody final DocumentAsset documentAsset,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			final DocumentAsset document = documentAssetService.createAsset(documentAsset, projectId, permission);
			return ResponseEntity.status(HttpStatus.CREATED).body(document);
		} catch (final IOException e) {
			final String error = "Unable to create document";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a document")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Document updated.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentAsset.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "Document could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the document", content = @Content)
		}
	)
	public ResponseEntity<DocumentAsset> updateDocument(
		@PathVariable("id") final UUID id,
		@RequestBody final DocumentAsset document,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		// if the document asset does not have an id, set it to the id in the path
		if (document.getId() == null) {
			document.setId(id);
		}

		try {
			final Optional<DocumentAsset> updated = documentAssetService.updateAsset(document, projectId, permission);
			if (updated.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(updated.get());
		} catch (final IOException e) {
			final String error = "Unable to update document";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets document by ID")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Document found.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentAsset.class)
				)
			),
			@ApiResponse(responseCode = "404", description = "There was no document found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the document from the data store",
				content = @Content
			)
		}
	)
	public ResponseEntity<DocumentAsset> getDocument(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanReadOrNone(
			currentUserService.get().getId(),
			projectId
		);

		final Optional<DocumentAsset> document = documentAssetService.getAsset(id, permission);
		if (document.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		// GETs not associated to a projectId cannot read private or temporary assets
		if (
			permission.equals(Schema.Permission.NONE) && (!document.get().getPublicAsset() || document.get().getTemporary())
		) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("rebac.unauthorized-read"));
		}

		// Test if the document as any assets
		if (document.get().getAssets() == null) {
			return ResponseEntity.ok(document.get());
		}

		document
			.get()
			.getAssets()
			.forEach(asset -> {
				try {
					// Add the S3 bucket url to each asset metadata
					final Optional<PresignedURL> url = documentAssetService.getDownloadUrl(id, asset.getFileName());
					if (url.isEmpty()) {
						return;
					}
					final PresignedURL presignedURL = url.get();
					asset.getMetadata().put("url", presignedURL.getUrl());
				} catch (final Exception e) {
					log.error("Unable to extract S3 url for assets or extract equations", e);
				}
			});

		// Return the updated document
		return ResponseEntity.ok(document.get());
	}

	@GetMapping("/{id}/upload-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to upload the document")
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
	public ResponseEntity<PresignedURL> getUploadURL(
		@PathVariable("id") final UUID id,
		@RequestParam("filename") final String filename
	) {
		try {
			return ResponseEntity.ok(documentAssetService.getUploadUrl(id, filename));
		} catch (final Exception e) {
			final String error = "Unable to get upload url";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/download-url")
	@Secured(Roles.USER)
	@Operation(summary = "Gets a presigned url to download the document")
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
			@ApiResponse(responseCode = "404", description = "Document could not be found", content = @Content),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue retrieving the presigned url",
				content = @Content
			)
		}
	)
	public ResponseEntity<PresignedURL> getDownloadURL(
		@PathVariable("id") final UUID id,
		@RequestParam("filename") final String filename
	) {
		try {
			final Optional<PresignedURL> url = documentAssetService.getDownloadUrl(id, filename);
			return url.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final Exception e) {
			final String error = "Unable to get download url";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes a document")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Delete document",
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
	public ResponseEntity<ResponseDeleted> deleteDocument(
		@PathVariable("id") final UUID id,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			documentAssetService.deleteAsset(id, projectId, permission);
			return ResponseEntity.ok(new ResponseDeleted("Document", id));
		} catch (final Exception e) {
			final String error = "Unable to delete document";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Uploads an artifact inside the entity to TDS via a presigned URL
	 *
	 * @param documentId The ID of the document to upload to
	 * @param fileName   The name of the file to upload
	 * @param fileEntity The entity containing the file to upload
	 * @return A response containing the status of the upload
	 */
	private ResponseEntity<Void> uploadDocumentHelper(
		final UUID documentId,
		final String fileName,
		final HttpEntity fileEntity,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		final Schema.Permission permission = projectService.checkPermissionCanWrite(
			currentUserService.get().getId(),
			projectId
		);

		try {
			// upload file to S3
			final Integer status = documentAssetService.uploadFile(documentId, fileName, fileEntity);
			final Optional<DocumentAsset> document = documentAssetService.getAsset(documentId, permission);

			if (document.isPresent()) {
				Graphics2D g2d = null;
				ByteArrayOutputStream outputStream = null;
				PDDocument pdfDocument = null;

				try {
					// Convert BufferedImage to byte[]
					pdfDocument = Loader.loadPDF(fileEntity.getContent().readAllBytes());
					PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);
					BufferedImage firstPageImage = pdfRenderer.renderImageWithDPI(0, 100);

					int height = firstPageImage.getHeight();
					int width = firstPageImage.getWidth();
					int topHalfHeight = height / 2;

					// Create a sub-image (top half)
					BufferedImage topHalfImage = firstPageImage.getSubimage(0, 0, width, topHalfHeight);

					// Resize the image to a width of 225px while maintaining aspect ratio
					int newWidth = 225;
					int newHeight = (newWidth * topHalfHeight) / width; // Maintain the aspect ratio

					// Create a new BufferedImage for the resized image
					Image resizedImage = topHalfImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
					BufferedImage resizedBufferedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

					g2d = resizedBufferedImage.createGraphics();
					g2d.drawImage(resizedImage, 0, 0, null);

					outputStream = new ByteArrayOutputStream();
					ImageIO.write(resizedBufferedImage, "png", outputStream);

					byte[] thumbnailBytes = outputStream.toByteArray();
					document.get().setThumbnail(thumbnailBytes);

					documentAssetService.updateAsset(document.get(), projectId, permission);
				} catch (final Exception e) {
					final String error = "Unable to create thumbnail";
					log.error(error, e);
				} finally {
					if (g2d != null) {
						g2d.dispose();
					}
					if (outputStream != null) {
						outputStream.close();
					}
					if (pdfDocument != null) {
						pdfDocument.close();
					}
				}
			}

			// if the fileEntity is not a PDF, then we need to extract the text and update
			// the document asset
			if (!DownloadService.IsPdf(fileEntity.getContent().readAllBytes())) {
				if (document.isEmpty()) {
					return ResponseEntity.notFound().build();
				}

				document.get().setText(IOUtils.toString(fileEntity.getContent(), StandardCharsets.UTF_8));
				documentAssetService.updateAsset(document.get(), projectId, permission);
			}

			return ResponseEntity.status(status).build();
		} catch (final IOException e) {
			final String error = "Unable to upload document";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/** Uploads a file to the project. */
	@PutMapping(value = "/{id}/upload-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a document")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Uploaded the document.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the document", content = @Content)
		}
	)
	public ResponseEntity<Void> uploadDocument(
		@PathVariable("id") final UUID id,
		@RequestParam("filename") final String filename,
		@RequestPart("file") final MultipartFile file,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		try {
			final byte[] fileAsBytes = file.getBytes();
			final HttpEntity fileEntity = new ByteArrayEntity(fileAsBytes, ContentType.APPLICATION_OCTET_STREAM);
			return uploadDocumentHelper(id, filename, fileEntity, projectId);
		} catch (final IOException e) {
			final String error = "Unable to upload document";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Downloads a file from GitHub given the path and owner name, then uploads it
	 * to the project.
	 */
	@PutMapping("/{documentId}/upload-document-from-github")
	@Secured(Roles.USER)
	@Operation(summary = "Uploads a document from github")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Uploaded the document.",
				content = @Content(
					mediaType = "application/json",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseStatus.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue uploading the document", content = @Content)
		}
	)
	public ResponseEntity<Void> uploadDocumentFromGithub(
		@PathVariable("documentId") final UUID documentId,
		@RequestParam("path") final String path,
		@RequestParam("repo-owner-and-name") final String repoOwnerAndName,
		@RequestParam("filename") final String filename,
		@RequestParam(name = "project-id", required = false) final UUID projectId
	) {
		log.debug("Uploading Document file from github to dataset {}", documentId);

		// download file from GitHub
		final String fileString = gitHubProxy.getGithubCode(repoOwnerAndName, path).getBody();
		if (fileString == null) {
			final String error = "Unable to download document from github";
			log.error(error);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
		final HttpEntity fileEntity = new StringEntity(fileString, ContentType.TEXT_PLAIN);
		return uploadDocumentHelper(documentId, filename, fileEntity, projectId);
	}

	@GetMapping(value = "/{id}/download-document", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	@Secured(Roles.USER)
	@Operation(summary = "Downloads a document")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Downloaded the document",
				content = @Content(
					mediaType = "application/octet-stream",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = byte[].class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue downloading the document",
				content = @Content
			)
		}
	)
	public ResponseEntity<byte[]> downloadDocument(
		@PathVariable("id") final UUID id,
		@RequestParam("filename") final String filename
	) {
		try {
			final Optional<byte[]> bytes = documentAssetService.fetchFileAsBytes(id, filename);
			if (bytes.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			final CacheControl cacheControl = CacheControl.maxAge(
				config.getCacheHeadersMaxAge(),
				TimeUnit.SECONDS
			).cachePublic();
			return ResponseEntity.ok().cacheControl(cacheControl).body(bytes.get());
		} catch (final Exception e) {
			final String error = "Unable to download document";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	@GetMapping("/{id}/download-document-as-text")
	@Secured(Roles.USER)
	@Operation(summary = "Downloads a document as text")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Downloaded the document",
				content = @Content(
					mediaType = "application/text",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = String.class)
				)
			),
			@ApiResponse(
				responseCode = "500",
				description = "There was an issue downloading the document",
				content = @Content
			)
		}
	)
	public ResponseEntity<String> getDocumentFileAsText(
		@PathVariable("id") final UUID documentId,
		@RequestParam("filename") final String filename
	) {
		log.debug("Downloading document file {} for document {}", filename, documentId);

		try {
			final Optional<String> file = documentAssetService.fetchFileAsString(documentId, filename);
			return file.map(ResponseEntity::ok).orElse(null);
		} catch (final Exception e) {
			final String error = "Unable to download document as text";
			log.error(error, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Post Images to Equations Unified service to get an AMR
	 *
	 * @param documentId document id
	 * @param filename   filename of the image
	 * @return LaTeX representation of the equation
	 */
	@GetMapping("/{id}/image-to-equation")
	@Secured(Roles.USER)
	@Operation(summary = "Post Images to Equations Unified service to get an AMR")
	@ApiResponses(
		value = {
			@ApiResponse(
				responseCode = "200",
				description = "Converts image to string",
				content = @Content(
					mediaType = "application/text",
					schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = String.class)
				)
			),
			@ApiResponse(responseCode = "500", description = "There was an issue creating equation", content = @Content)
		}
	)
	public ResponseEntity<String> postImageToEquation(
		@PathVariable("id") final UUID documentId,
		@RequestParam("filename") final String filename
	) {
		Optional<byte[]> bytes = Optional.empty();
		try {
			bytes = documentAssetService.fetchFileAsBytes(documentId, filename);
		} catch (IOException e) {
			log.error("Unable to fetch files from the document asset service", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("generic.io-error.read"));
		}

		if (bytes.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		final byte[] imagesByte = bytes.get();

		// Encode the image in Base 64
		final String imageB64 = Base64.getEncoder().encodeToString(imagesByte);

		// image -> mathML
		final String mathML = skemaUnifiedProxy.postImageToEquations(imageB64).getBody();

		// mathML -> LaTeX
		final String latex = skemaRustProxy.convertMathML2Latex(mathML).getBody();
		if (latex == null) {
			log.error("Unable to convert MathML to LaTeX");
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("skema.error.image-2-equation"));
		}

		// Add spaces before and after "*"
		String latexWithSpaces = latex.replaceAll("(?<!\\s)\\*", " *");
		latexWithSpaces = latexWithSpaces.replaceAll("\\*(?!\\s)", "* ");

		return ResponseEntity.ok(latexWithSpaces);
	}
}
