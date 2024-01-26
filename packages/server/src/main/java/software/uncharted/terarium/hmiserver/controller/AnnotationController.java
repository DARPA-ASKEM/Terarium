package software.uncharted.terarium.hmiserver.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.user.Annotation;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.AnnotationService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;

@RequestMapping("/annotations")
@RestController
@Slf4j
public class AnnotationController {

	@Autowired
	private AnnotationService annotationService;

	@Autowired
	private CurrentUserService currentUserService;

	@GetMapping
	@Secured(Roles.USER)
	public ResponseEntity<List<Annotation>> getAnnotations(
			@RequestParam("artifact-type") final String artifactType,
			@RequestParam("artifact-id") final String artifactId,
			@RequestParam(value = "limit", defaultValue = "100", required = false) final int limit) {

		return ResponseEntity
				.ok(annotationService.findArtifacts(artifactType, artifactId, limit));
	}

	@PostMapping
	@Secured(Roles.USER)
	@Transactional
	public ResponseEntity<Annotation> postEvent(@RequestBody final Annotation annotation) {
		annotation.setUserId(currentUserService.get().getId());

		return ResponseEntity
				.ok(annotationService.save(annotation));
	}

	@PatchMapping
	@Secured(Roles.USER)
	@Transactional
	public ResponseEntity<Annotation> updateAnnotation(@RequestBody final Annotation newAnnotation) {
		final String id = newAnnotation.getId();
		final String content = newAnnotation.getContent();
		final String section = newAnnotation.getSection();
		if (id == null || content == null || section == null) {
			return ResponseEntity.badRequest()
					.build();
		}
		final Annotation annotation = annotationService.findArtifact(id);
		if (annotation == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annotation not found");
		}
		annotation.setContent(content);
		annotation.setSection(section);
		annotation.setTimestampMillis(Instant.now().toEpochMilli());
		return ResponseEntity
				.ok(annotationService.save(annotation));
	}

	@DeleteMapping
	@Secured(Roles.USER)
	@Transactional
	public ResponseEntity<JsonNode> deleteAnnotations(@RequestParam("id") final String id) {

		final Annotation annotation = annotationService.findArtifact(id);
		if (annotation == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annotation not found");
		}
		annotationService.delete(id);
		return ResponseEntity.ok().build();
	}
}
