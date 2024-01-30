package software.uncharted.terarium.taskrunner.configuration;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitConfiguration {
	@Value("${spring.rabbitmq.addresses}")
	String rabbitAddresses;

	@Value("${spring.rabbitmq.username}")
	String username;

	@Value("${spring.rabbitmq.password}")
	String password;

	@Bean
	public RabbitAdmin rabbitAdmin() throws URISyntaxException {

		URI rabbitAddress = new URI(rabbitAddresses);

		log.info("Connecting to RabbitMQ: {}", rabbitAddress);

		final CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
		connectionFactory.setUri(rabbitAddress);
		connectionFactory.setUsername(username);
		connectionFactory.setPassword(password);
		return new RabbitAdmin(connectionFactory);
	}
}
