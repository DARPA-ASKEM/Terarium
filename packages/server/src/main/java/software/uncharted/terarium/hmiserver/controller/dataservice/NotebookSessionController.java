package software.uncharted.terarium.hmiserver.controller.dataservice;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.dataservice.ResponseDeleted;
import software.uncharted.terarium.hmiserver.models.dataservice.notebooksession.NotebookSession;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.data.NotebookSessionService;

/**
 * Rest controller for storing, retrieving, modifying and deleting notebook
 * sessions in the dataservice
 */
@RequestMapping("/sessions")
@RestController
@Slf4j
@RequiredArgsConstructor
public class NotebookSessionController {

	final NotebookSessionService sessionService;
	final ObjectMapper objectMapper;

	/**
	 * Retrieve the list of NotebookSessions
	 *
	 * @param pageSize number of sessions per page
	 * @param page     current page number
	 * @return list of sessions
	 */
	@GetMapping
	@Secured(Roles.USER)
	@Operation(summary = "Gets all sessions")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "NotebookSessions found.", content = @Content(array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NotebookSession.class)))),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving sessions from the data store", content = @Content)
	})
	ResponseEntity<List<NotebookSession>> getNotebookSessions(
			@RequestParam(name = "page-size", defaultValue = "100") final Integer pageSize,
			@RequestParam(name = "page", defaultValue = "0") final Integer page) {

		try {
			return ResponseEntity.ok(sessionService.getAssets(pageSize, page));
		} catch (final IOException e) {
			final String error = "Unable to get sessions";
			log.error(error, e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					error);
		}
	}

	/**
	 * Create session and return its ID
	 *
	 * @param session session to create
	 * @return new ID for session
	 */
	@PostMapping
	@Secured(Roles.USER)
	@Operation(summary = "Create a new session")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "NotebookSession created.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NotebookSession.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue creating the session", content = @Content)
	})
	ResponseEntity<NotebookSession> createNotebookSession(@RequestBody final NotebookSession session) {

		try {
			sessionService.createAsset(session);
			return ResponseEntity.status(HttpStatus.CREATED).body(session);
		} catch (final IOException e) {
			final String error = "Unable to create session";
			log.error(error, e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					error);
		}
	}

	/**
	 * Retrieve an session
	 *
	 * @param id session id
	 * @return NotebookSession
	 */
	@GetMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Gets session by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "NotebookSession found.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NotebookSession.class))),
			@ApiResponse(responseCode = "404", description = "There was no session found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue retrieving the session from the data store", content = @Content)
	})
	ResponseEntity<NotebookSession> getNotebookSession(@PathVariable("id") final UUID id) {

		try {
			final Optional<NotebookSession> session = sessionService.getAsset(id);
			return session.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
		} catch (final IOException e) {
			final String error = "Unable to get notebook session";
			log.error(error, e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					error);
		}
	}

	/**
	 * Update an session
	 *
	 * @param id      id of session to update
	 * @param session session to update with
	 * @return ID of updated session
	 */
	@PutMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Update a session")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "NotebookSession updated.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NotebookSession.class))),
			@ApiResponse(responseCode = "404", description = "NotebookSession could not be found", content = @Content),
			@ApiResponse(responseCode = "500", description = "There was an issue updating the session", content = @Content)
	})
	ResponseEntity<NotebookSession> updateNotebookSession(
			@PathVariable("id") final UUID id,
			@RequestBody final NotebookSession session) {

		try {
			session.setId(id);
			return ResponseEntity.ok(sessionService.updateAsset(session));
		} catch (final IOException e) {
			final String error = "Unable to update notebook session";
			log.error(error, e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					error);
		}
	}

	@PostMapping("/{id}/clone")
	@Secured(Roles.USER)
	@Operation(summary = "Clone a session")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "NotebookSession cloned.", content = @Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NotebookSession.class))),
			@ApiResponse(responseCode = "500", description = "There was an issue cloning the session", content = @Content)
	})
	ResponseEntity<NotebookSession> cloneNotebookSession(
			@PathVariable("id") final UUID id) {
		try {
			final NotebookSession clone = sessionService.cloneAsset(id);
			return ResponseEntity.status(HttpStatus.CREATED).body(clone);
		} catch (final IOException e) {
			final String error = "Unable to clone notebook session";
			log.error(error, e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					error);
		}
	}

	/**
	 * Deletes and session
	 *
	 * @param id session to delete
	 * @return delete message
	 */
	@DeleteMapping("/{id}")
	@Secured(Roles.USER)
	@Operation(summary = "Deletes an session")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Deleted session", content = {
					@Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResponseDeleted.class)) }),
			@ApiResponse(responseCode = "500", description = "An error occurred while deleting", content = @Content)
	})
	ResponseEntity<ResponseDeleted> deleteNotebookSession(@PathVariable("id") final UUID id) {

		try {
			sessionService.deleteAsset(id);
			return ResponseEntity.ok(new ResponseDeleted("NotebookSession", id));
		} catch (final IOException e) {
			final String error = "Unable to delete noteboko session";
			log.error(error, e);
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					error);
		}
	}

}
