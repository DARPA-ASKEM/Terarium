package software.uncharted.terarium.hmiserver.controller.user;


import org.jboss.resteasy.annotations.SseElementType;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.uncharted.terarium.hmiserver.models.user.UserEvent;


import javax.ws.rs.core.MediaType;

@RequestMapping("user/")
@RestController
public class ServerSentEventController {


	//@Autowired
	//@Channel("user-event") Publisher<UserEvent> userEvents;

	/**
	 * Gets all user events
	 */
	@GetMapping(name ="/server-sent-events", produces = MediaType.SERVER_SENT_EVENTS)
	@SseElementType(MediaType.APPLICATION_JSON)
	public ResponseEntity<Publisher<UserEvent>> stream() {

		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();

		//return Multi.createFrom().publisher(userEvents);
	}
}
