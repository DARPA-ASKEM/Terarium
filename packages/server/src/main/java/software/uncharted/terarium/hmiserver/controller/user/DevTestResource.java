package software.uncharted.terarium.hmiserver.controller.user;

import com.fasterxml.jackson.databind.JsonNode;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.uncharted.terarium.hmiserver.models.user.UserEvent;

import java.util.UUID;

@RequestMapping("/dev-tests")
@RestController
public class DevTestResource {
	//@Broadcast
	//@Channel("user-event")
	//Emitter<UserEvent> userEventEmitter;

	@PutMapping("/user-event")

	public ResponseEntity<JsonNode> createModel() {
		final UUID id = UUID.randomUUID();
		final UserEvent event = new UserEvent();
		//event.setId(id);
		//userEventEmitter.send(event);
		//return Response.ok(Map.of("id", id.toString())).build();
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}
}
