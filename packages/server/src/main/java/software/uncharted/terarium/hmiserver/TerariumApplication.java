package software.uncharted.terarium.hmiserver;

import java.util.concurrent.Executor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@PropertySource("classpath:application.properties")
@EnableAsync
public class TerariumApplication {

	public static void main(final String[] args) {
		SpringApplication.run(TerariumApplication.class, args);
	}

	/**
	 * Configures the thread pool for <a href="https://spring.io/guides/gs/async-method">async tasks</a>
	 * @return the executor
	 */
	@Bean
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("AsyncService-");
		executor.initialize();
		return executor;
	}
}
