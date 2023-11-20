package software.uncharted.terarium.hmiserver.controller;


import com.fasterxml.jackson.databind.JsonNode;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.models.user.Annotation;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.AnnotationService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;

import java.time.Instant;
import java.util.List;

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
		@RequestParam("artifact_type") final String artifactType,
		@RequestParam("artifact_id") final String artifactId,
		@RequestParam(value = "limit", defaultValue = "100", required = false)final int limit) {

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
	public ResponseEntity<Annotation> updateAnnotation(@RequestBody final Annotation newAnnotation){
		final String id = newAnnotation.getId();
		final String content = newAnnotation.getContent();
		final String section = newAnnotation.getSection();
		if (id == null || content == null || section == null) {
			return ResponseEntity.badRequest()
				.build();
		}
		final Annotation annotation = annotationService.findArtifact(id);
		if (annotation == null){
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
		if (annotation == null){
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annotation not found");
		}
		annotationService.delete(id);
		return ResponseEntity.ok().build();
	}
}

