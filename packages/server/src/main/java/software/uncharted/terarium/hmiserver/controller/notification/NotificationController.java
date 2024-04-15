package software.uncharted.terarium.hmiserver.controller.notification;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.models.notification.NotificationGroup;
import software.uncharted.terarium.hmiserver.security.Roles;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;
import software.uncharted.terarium.hmiserver.service.notification.NotificationService;

@RequestMapping("/notification")
@RestController
@Slf4j
@RequiredArgsConstructor
public class NotificationController {

	private final CurrentUserService currentUserService;
	private final NotificationService notificationService;

	@GetMapping("/user")
	@Secured(Roles.USER)
	@Operation(summary = "Return all recent notification groups for a user")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Returned recent notifications successfully", content = @Content(array = @ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NotificationGroup.class)))),
			@ApiResponse(responseCode = "500", description = "There was an issue fetching the notifications", content = @Content)
	})
	public ResponseEntity<List<NotificationGroup>> getNotificationGroups(
			@RequestParam(value = "since", required = false, defaultValue = "2") final long sinceInHours) {

		final LocalDateTime localDateTime = LocalDateTime.now().minusHours(sinceInHours);
		final Timestamp since = Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());

		final String userId = currentUserService.get().getId().toString();

		return ResponseEntity.ok(notificationService.getNotificationGroupsCreatedSince(userId, since));
	}

}
