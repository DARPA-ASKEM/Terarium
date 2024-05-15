package software.uncharted.terarium.hmiserver.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.ClientEvent;
import software.uncharted.terarium.hmiserver.models.ClientEventType;
import software.uncharted.terarium.hmiserver.models.dataservice.simulation.ProgressState;
import software.uncharted.terarium.hmiserver.models.extractionservice.ExtractionStatusUpdate;
import software.uncharted.terarium.hmiserver.models.notification.NotificationEvent;
import software.uncharted.terarium.hmiserver.models.notification.NotificationGroup;
import software.uncharted.terarium.hmiserver.service.CurrentUserService;

@Slf4j
public class NotificationServiceTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private CurrentUserService currentUserService;

	ClientEvent<ExtractionStatusUpdate> produceClientEvent(final Double t, final String message, final String error) {
		final ExtractionStatusUpdate update = new ExtractionStatusUpdate(UUID.randomUUID(), t, message, error);
		return ClientEvent.<ExtractionStatusUpdate>builder()
				.type(ClientEventType.HEARTBEAT)
				.data(update)
				.build();
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateNotificationGroup() throws Exception {

		final NotificationGroup group =
				notificationService.createNotificationGroup(new NotificationGroup().setType("test"));

		Assertions.assertNotNull(group.getId());
		Assertions.assertNotNull(group.getCreatedOn());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateNotificationEvent() throws Exception {

		final NotificationGroup group =
				notificationService.createNotificationGroup(new NotificationGroup().setType("test"));

		notificationService.createNotificationEvent(
				group.getId(),
				new NotificationEvent().setData(produceClientEvent(1.0, "", "")).setState(ProgressState.QUEUED));
		notificationService.createNotificationEvent(
				group.getId(),
				new NotificationEvent().setData(produceClientEvent(1.0, "", "")).setState(ProgressState.RUNNING));
		notificationService.createNotificationEvent(
				group.getId(),
				new NotificationEvent().setData(produceClientEvent(1.0, "", "")).setState(ProgressState.CANCELLED));

		final NotificationGroup after =
				notificationService.getNotificationGroup(group.getId()).orElseThrow();

		Assertions.assertNotNull(after.getId());
		Assertions.assertNotNull(after.getCreatedOn());
		Assertions.assertNotNull(after.getNotificationEvents());
		Assertions.assertEquals(3, after.getNotificationEvents().size());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanAckNotificationGroup() throws Exception {

		final NotificationGroup group =
				notificationService.createNotificationGroup(new NotificationGroup().setType("test"));

		notificationService.createNotificationEvent(
				group.getId(), new NotificationEvent().setData(produceClientEvent(1.0, "", "")));
		notificationService.createNotificationEvent(
				group.getId(), new NotificationEvent().setData(produceClientEvent(1.0, "", "")));
		notificationService.createNotificationEvent(
				group.getId(), new NotificationEvent().setData(produceClientEvent(1.0, "", "")));

		final NotificationGroup after =
				notificationService.getNotificationGroup(group.getId()).orElseThrow();

		Assertions.assertNotNull(after.getId());
		Assertions.assertNotNull(after.getCreatedOn());
		Assertions.assertNotNull(after.getNotificationEvents());
		Assertions.assertEquals(3, after.getNotificationEvents().size());
		for (final NotificationEvent event : after.getNotificationEvents()) {
			Assertions.assertNotNull(event.getId());
			Assertions.assertNotNull(event.getCreatedOn());
			Assertions.assertNotNull(event.getData());
			Assertions.assertNull(event.getAcknowledgedOn());
		}

		final LocalDateTime sinceDateTime = LocalDateTime.now().minusHours(2);
		final Timestamp since =
				Timestamp.from(sinceDateTime.atZone(ZoneId.systemDefault()).toInstant());

		final List<NotificationGroup> resp1 = notificationService.getUnAckedNotificationGroupsCreatedSince(
				currentUserService.get().getId(), since);

		Assertions.assertEquals(1, resp1.size());
		Assertions.assertNotNull(resp1.get(0).getNotificationEvents());
		Assertions.assertEquals(3, resp1.get(0).getNotificationEvents().size());

		// ack the group
		notificationService.acknowledgeNotificationGroup(group.getId());

		final List<NotificationGroup> resp2 = notificationService.getUnAckedNotificationGroupsCreatedSince(
				currentUserService.get().getId(), since);

		Assertions.assertEquals(0, resp2.size());

		// create a new event
		notificationService.createNotificationEvent(
				group.getId(), new NotificationEvent().setData(produceClientEvent(1.0, "", "")));

		final List<NotificationGroup> resp3 = notificationService.getUnAckedNotificationGroupsCreatedSince(
				currentUserService.get().getId(), since);

		Assertions.assertEquals(1, resp3.size());
		Assertions.assertNotNull(resp3.get(0).getNotificationEvents());
		Assertions.assertEquals(4, resp3.get(0).getNotificationEvents().size());
	}
}
