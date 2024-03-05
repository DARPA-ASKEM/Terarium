package software.uncharted.terarium.hmiserver.service.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithUserDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.controller.mira.MiraController;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.service.tasks.TaskService.TaskType;

@Slf4j
public class TaskServiceTest extends TerariumApplicationTests {

	@Autowired
	private TaskService taskService;

	@Autowired
	private RedissonClient redissonClient;

	@BeforeEach
	public void setup() throws IOException {
		// remove everything from redis
		redissonClient.getKeys().flushall();
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateEchoTaskRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();
		final String additionalProps = "These are additional properties";

		final byte[] input = "{\"input\":\"This is my input string\"}".getBytes();

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript("/echo.py");
		req.setInput(input);
		req.setAdditionalProperties(additionalProps);

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.GOLLM);

		Assertions.assertEquals(taskId, resp.getId());
		Assertions.assertEquals(additionalProps, resp.getAdditionalProperties(String.class));
	}

	private String generateRandomString(final int length) {
		final String characterSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		final Random random = new Random();
		final StringBuilder builder = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			final int randomIndex = random.nextInt(characterSet.length());
			builder.append(characterSet.charAt(randomIndex));
		}

		return builder.toString();
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateLargeEchoTaskRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();
		final String additionalProps = "These are additional properties";

		final int STRING_LENGTH = 1048576;

		final byte[] input = ("{\"input\":\"" + generateRandomString(STRING_LENGTH) + "\"}").getBytes();

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript("/echo.py");
		req.setInput(input);
		req.setAdditionalProperties(additionalProps);

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.GOLLM);

		Assertions.assertEquals(taskId, resp.getId());
		Assertions.assertEquals(additionalProps, resp.getAdditionalProperties(String.class));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMModelCardRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();

		final ClassPathResource resource = new ClassPathResource("gollm/test_input.json");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript("gollm:model_card");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.GOLLM, 300);

		Assertions.assertEquals(taskId, resp.getId());

		log.info(new String(resp.getOutput()));
	}

	static class AdditionalProps {
		public String str;
		public Integer num;
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMEmbeddingRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript("gollm:embedding");
		req.setInput(
				("{\"text\":\"What kind of dinosaur is the coolest?\",\"embedding_model\":\"text-embedding-ada-002\"}")
						.getBytes());

		final AdditionalProps add = new AdditionalProps();
		add.str = "this is my str";
		add.num = 123;
		req.setAdditionalProperties(add);

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.GOLLM);

		Assertions.assertEquals(taskId, resp.getId());

		final AdditionalProps respAdd = resp.getAdditionalProperties(AdditionalProps.class);
		Assertions.assertEquals(add.str, respAdd.str);
		Assertions.assertEquals(add.num, respAdd.num);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendMiraMDLToStockflowRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();

		final ClassPathResource resource = new ClassPathResource("mira/IndiaNonSubscriptedPulsed.mdl");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript(MiraController.MDL_TO_STOCKFLOW);
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.MIRA);

		Assertions.assertEquals(taskId, resp.getId());

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendMiraStellaToStockflowRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();

		final ClassPathResource resource = new ClassPathResource("mira/SIR.xmile");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript(MiraController.STELLA_TO_STOCKFLOW);
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.MIRA);

		Assertions.assertEquals(taskId, resp.getId());

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendMiraSBMLToPetrinetRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();

		final ClassPathResource resource = new ClassPathResource("mira/BIOMD0000000001.xml");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript(MiraController.SBML_TO_PETRINET);
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.MIRA);

		Assertions.assertEquals(taskId, resp.getId());

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMConfigFromDatasetRequest() throws Exception {

		final UUID taskId = UUID.randomUUID();

		final ClassPathResource datasetResource1 = new ClassPathResource("gollm/Epi Sc 4 Interaction matrix.csv");
		final String dataset1 = new String(Files.readAllBytes(datasetResource1.getFile().toPath()));
		final ClassPathResource datasetResource2 = new ClassPathResource("gollm/other-dataset.csv");
		final String dataset2 = new String(Files.readAllBytes(datasetResource2.getFile().toPath()));

		final ClassPathResource amrResource = new ClassPathResource("gollm/scenario4_4spec_regnet_empty.json");
		final String amr = new String(Files.readAllBytes(amrResource.getFile().toPath()));
		final JsonNode amrJson = new ObjectMapper().readTree(amr);

		final String content = "{\"datasets\": ["
				+ "\"" + dataset1.replaceAll("(?<!\\\\)\\n", Matcher.quoteReplacement("\\\\n")) + "\","
				+ "\"" + dataset2.replaceAll("(?<!\\\\)\\n", Matcher.quoteReplacement("\\\\n"))
				+ "\"], \"amr\":"
				+ amrJson.toString() + "}";

		final TaskRequest req = new TaskRequest();
		req.setId(taskId);
		req.setScript("gollm:dataset_configure");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req, TaskType.GOLLM);

		Assertions.assertEquals(taskId, resp.getId());

		log.info(new String(resp.getOutput()));
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCacheWithConcurrency() throws Exception {

		final int NUM_REQUESTS = 512;
		final int NUM_UNIQUE_REQUESTS = 16;
		final int NUM_THREADS = 12;
		final int TIMEOUT_SECONDS = 20;

		final List<byte[]> reqInput = new ArrayList<>();
		for (int i = 0; i < NUM_UNIQUE_REQUESTS; i++) {
			// success tasks
			reqInput.add(("{\"input\":\"" + generateRandomString(1024) + "\"}").getBytes());
		}
		for (int i = 0; i < NUM_UNIQUE_REQUESTS; i++) {
			// failure tasks
			reqInput.add(("{\"input\":\"" + generateRandomString(1024) + "\", \"should_fail\": true}").getBytes());
		}

		final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

		final Set<UUID> successTaskIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
		// final Set<UUID> failedTaskIds = Collections.newSetFromMap(new
		// ConcurrentHashMap<>());
		final List<Future<?>> futures = new ArrayList<>();

		final Random rand = new Random();

		for (int i = 0; i < NUM_REQUESTS; i++) {
			final Future<?> future = executor.submit(() -> {
				try {
					final TaskRequest req = new TaskRequest();
					req.setScript("/echo.py");
					req.setInput(reqInput.get(rand.nextInt(NUM_UNIQUE_REQUESTS)));

					final TaskResponse resp = taskService.runTaskSync(req, TaskType.GOLLM, TIMEOUT_SECONDS);
					successTaskIds.add(resp.getId());
				} catch (final RuntimeException e) {
					// expected in some cases

				} catch (final Exception e) {
					log.error("Error in test", e);
				}
			});
			futures.add(future);
		}

		// wait for all the responses to be send
		for (final Future<?> future : futures) {
			future.get(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);
		}

		log.info("Sending {} requests, which resulted in {} unique dispatches", NUM_REQUESTS, successTaskIds.size());
		for (final UUID taskId : successTaskIds) {
			log.info("Task ID: {}", taskId.toString());
		}
		Assertions.assertTrue(successTaskIds.size() <= NUM_UNIQUE_REQUESTS);

	}

}
