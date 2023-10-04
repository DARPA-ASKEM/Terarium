package software.uncharted.terarium.hmiserver.controller;


import com.fasterxml.jackson.databind.JsonNode;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.uncharted.terarium.hmiserver.entities.Annotation;
import software.uncharted.terarium.hmiserver.service.AnnotationService;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;


import javax.ws.rs.*;
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

	@GET
	public ResponseEntity<List<Annotation>> getAnnotations(
		@RequestParam("artifact_type") final String artifactType,
		@RequestParam("artifact_id") final String artifactId,
		@RequestParam(value = "limit", defaultValue = "100", required = false)final int limit) {

		return ResponseEntity
			.ok(annotationService.findArtifacts(artifactType, artifactId, limit));
	}

	@PostMapping

	@Transactional
	public ResponseEntity<Annotation> postEvent(@RequestBody final Annotation annotation) {
		annotation.setUserId(currentUserService.get().getId());

		return ResponseEntity
			.ok(annotationService.save(annotation));
	}

	@PatchMapping
	@Transactional
	public ResponseEntity<Annotation> updateAnnotation(@RequestBody final Annotation newAnnotation){
		String id = newAnnotation.getId();
		String content = newAnnotation.getContent();
		String section = newAnnotation.getSection();
		if (id == null || content == null || section == null) {
			return ResponseEntity.badRequest()
				.build();
		}
		Annotation annotation = annotationService.findArtifact(id);
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
	@Transactional
	public ResponseEntity<JsonNode> deleteAnnotations(@RequestParam("id") final String id) {

		Annotation annotation = annotationService.findArtifact(id);
		if (annotation == null){
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annotation not found");
		}
		annotationService.delete(id);
		return ResponseEntity.ok().build();
	}
}

