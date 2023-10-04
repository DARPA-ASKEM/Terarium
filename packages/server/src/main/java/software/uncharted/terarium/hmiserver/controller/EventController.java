package software.uncharted.terarium.hmiserver.controller;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.uncharted.terarium.hmiserver.annotations.IgnoreRequestLogging;
import software.uncharted.terarium.hmiserver.entities.Event;
import software.uncharted.terarium.hmiserver.models.EventType;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.EventService;

import java.util.List;

@RequestMapping("/events")
@RestController
@Slf4j
public class EventController {
	@Autowired
	private EventService eventService;

	@Autowired
	private CurrentUserService currentUserService;


	/**
	 * Gets a list of events sorted by timestamp descending
	 * @param type				the {@link EventType} of the events to fetch
	 * @param projectId		the projectId to fetch events for
	 * @param limit				the number of events to fetch
	 * @return						a list of {@link Event} for the given user/project/type sorted by most to least recent
	 */
	@GetMapping
	public ResponseEntity<List<Event>> getEvents(@RequestParam(value = "type") final EventType type,
																								 @RequestParam(value = "projectId", required = false) final Long projectId,
																								 @RequestParam(value = "search", required = false) final String likeValue,
																								 @RequestParam(value = "limit", defaultValue = "10") final int limit) {

		return ResponseEntity.ok(eventService.findEvents(type, projectId, currentUserService.get().getId(), likeValue, limit));
	}

	/**
	 * Create an event
	 * @param event	the {@link Event} instance
	 * @return			the persisted event instance
	 */
	@PostMapping
	@Transactional
	@IgnoreRequestLogging
	public ResponseEntity<Event> postEvent(@RequestBody final Event event) {
		event.setUsername(currentUserService.get().getId());

		// Do not save the event to the database if the type is not specified as persistent
		if (!event.getType().isPersistent()) {
			return ResponseEntity
				.ok(null);
		}

		return ResponseEntity.ok(eventService.save(event));
	}
}
