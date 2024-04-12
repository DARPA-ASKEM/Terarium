package software.uncharted.terarium.hmiserver.repository.notification;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import jakarta.validation.constraints.NotNull;
import software.uncharted.terarium.hmiserver.models.notification.NotificationGroup;
import software.uncharted.terarium.hmiserver.repository.PSCrudRepository;

@Repository
public interface NotificationGroupRepository extends PSCrudRepository<NotificationGroup, UUID> {

	List<NotificationGroup> findAllByUserIdOrderByTimestampDesc(@NotNull String userId);

	List<NotificationGroup> findAllByUserIdAndTimestampGreaterThanOrderByTimestampDesc(@NotNull String userId,
			@NotNull Timestamp since);
}
