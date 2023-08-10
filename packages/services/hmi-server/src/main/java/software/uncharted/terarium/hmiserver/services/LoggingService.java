package software.uncharted.terarium.hmiserver.services;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
@Data
@Accessors(chain = true)
public class LoggingService {
	private List<LogMessage> logs;

	public void logMessage(LogMessage logObj, String name) {
		if (logObj.message == null || logObj.message.trim().isEmpty()) {
			return;
		}
		String level = logObj.level;
		String message = "HMI_LOG | " + name + " | " + logObj.message;
		switch (level) {
			case "trace":
				log.trace(message);
				break;
			case "debug":
				log.debug(message);
				break;
			case "info":
				log.info(message);
				break;
			case "warn":
				log.warn(message);
				break;
			case "error":
				log.error(message);
				break;
			default:
				log.info("Invalid logging level, defaulting to info level: " + message);
				break;
		}
	}

}
