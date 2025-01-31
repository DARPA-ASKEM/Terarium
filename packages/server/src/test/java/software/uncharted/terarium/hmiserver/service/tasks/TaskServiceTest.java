package software.uncharted.terarium.hmiserver.service.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithUserDetails;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.controller.mira.MiraController.ConversionAdditionalProperties;
import software.uncharted.terarium.hmiserver.models.task.TaskFuture;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest;
import software.uncharted.terarium.hmiserver.models.task.TaskRequest.TaskType;
import software.uncharted.terarium.hmiserver.models.task.TaskResponse;
import software.uncharted.terarium.hmiserver.models.task.TaskStatus;

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
		final String additionalProps = "These are additional properties";

		final byte[] input = "{\"input\":\"This is my input string\",\"include_progress\":true}".getBytes();

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("echo.py");
		req.setInput(input);
		req.setAdditionalProperties(additionalProps);

		final TaskResponse resp = taskService.runTaskSync(req);

		Assertions.assertEquals(additionalProps, resp.getAdditionalProperties(String.class));
	}

	private static String generateRandomString(final int length) {
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
		final String additionalProps = "These are additional properties";

		final int STRING_LENGTH = 1048576;

		final byte[] input =
			("{\"input\":\"" + generateRandomString(STRING_LENGTH) + "\",\"include_progress\":true}").getBytes();

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("echo.py");
		req.setInput(input);
		req.setAdditionalProperties(additionalProps);

		final TaskResponse resp = taskService.runTaskSync(req);

		Assertions.assertEquals(additionalProps, resp.getAdditionalProperties(String.class));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMModelCardRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("gollm/test_input.json");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("gollm:model_card");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMEnrichAMRRequest() throws Exception {
		final ClassPathResource modelResource = new ClassPathResource("gollm/SIR.json");
		final String modelContent = new String(Files.readAllBytes(modelResource.getFile().toPath()));

		final ClassPathResource documentResource = new ClassPathResource("gollm/SIR.txt");
		final String documentContent = new String(Files.readAllBytes(documentResource.getFile().toPath()));

		final EnrichAmrResponseHandler.Input input = new EnrichAmrResponseHandler.Input();
		input.setResearchPaper(documentContent);
		input.setAmr(modelContent);

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("gollm:enrich_amr");
		req.setInput(input);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	static class AdditionalProps {

		public String str;
		public Integer num;
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMEmbeddingRequest() throws Exception {
		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("gollm:embedding");
		req.setInput(
			("{\"text\": [\"What kind of dinosaur is the coolest?\"],\"embedding_model\":\"text-embedding-ada-002\"}").getBytes()
		);

		final AdditionalProps add = new AdditionalProps();
		add.str = "this is my str";
		add.num = 123;
		req.setAdditionalProperties(add);

		final TaskResponse resp = taskService.runTaskSync(req);

		final AdditionalProps respAdd = resp.getAdditionalProperties(AdditionalProps.class);
		Assertions.assertEquals(add.str, respAdd.str);
		Assertions.assertEquals(add.num, respAdd.num);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMGenerateSummaryRequest() throws Exception {
		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript(GenerateSummaryHandler.NAME);
		final String input =
			"Following sections describe the input and output of an operation.\nInput: { a: 1}\nOutput: { a: 2}. Provide a summary in less than 10 words.";
		req.setInput(input.getBytes(StandardCharsets.UTF_8));

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMGenerateResponseRequest() throws Exception {
		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript(ChartAnnotationResponseHandler.NAME);
		final ChartAnnotationResponseHandler.Input input = new ChartAnnotationResponseHandler.Input();
		input.setPreamble("This is a preamble");
		input.setInstruction("Give me a simple random json object");
		req.setInput(input);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendEquationExtractionGenerateResponseRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("equation/SIR.pdf");
		final byte[] content = Files.readAllBytes(resource.getFile().toPath());

		final int TIMEOUT_MINUTES = 5;
		final TaskRequest req = new TaskRequest();
		req.setTimeoutMinutes(TIMEOUT_MINUTES);
		req.setInput(content);
		req.setType(TaskType.EQUATION_EXTRACTION_CPU);
		req.setScript(ExtractEquationsResponseHandler.NAME);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendMiraMDLToStockflowRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("mira/IndiaNonSubscriptedPulsed.mdl");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final ConversionAdditionalProperties additionalProperties = new ConversionAdditionalProperties();
		additionalProperties.setFileName("IndiaNonSubscriptedPulsed.mdl");

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.MIRA);
		req.setScript("mira_task:mdl_to_stockflow");
		req.setInput(content.getBytes());
		req.setAdditionalProperties(additionalProperties);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendMiraStellaToStockflowRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("mira/SIR.xmile");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final ConversionAdditionalProperties additionalProperties = new ConversionAdditionalProperties();
		additionalProperties.setFileName("SIR.xmile");

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.MIRA);
		req.setScript("mira_task:stella_to_stockflow");
		req.setInput(content.getBytes());
		req.setAdditionalProperties(additionalProperties);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendMiraSBMLToPetrinetRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("mira/BIOMD0000000001.xml");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.MIRA);
		req.setScript("mira_task:sbml_to_petrinet");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMConfigFromDatasetRequest() throws Exception {
		final ClassPathResource datasetResource1 = new ClassPathResource("gollm/Epi Sc 4 Interaction matrix.csv");
		final String dataset1 = new String(Files.readAllBytes(datasetResource1.getFile().toPath()));
		final ClassPathResource datasetResource2 = new ClassPathResource("gollm/other-dataset.csv");
		final String dataset2 = new String(Files.readAllBytes(datasetResource2.getFile().toPath()));

		final ClassPathResource amrResource = new ClassPathResource("gollm/scenario4_4spec_regnet_empty.json");
		final String amr = new String(Files.readAllBytes(amrResource.getFile().toPath()));
		final JsonNode amrJson = new ObjectMapper().readTree(amr);

		final String content =
			"{\"datasets\": [" +
			"\"" +
			dataset1.replaceAll("(?<!\\\\)\\n", Matcher.quoteReplacement("\\\\n")) +
			"\"," +
			"\"" +
			dataset2.replaceAll("(?<!\\\\)\\n", Matcher.quoteReplacement("\\\\n")) +
			"\"], \"amr\":" +
			amrJson.toString() +
			"}";

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("gollm:configure_model_from_dataset");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGoLLMInterventionsFromDocumentRequest() throws Exception {
		final ClassPathResource modelResource = new ClassPathResource("gollm/SIR.json");
		final String modelContent = new String(Files.readAllBytes(modelResource.getFile().toPath()));

		final ClassPathResource documentResource = new ClassPathResource("gollm/SIR.txt");
		final String documentContent = new String(Files.readAllBytes(documentResource.getFile().toPath()));

		final InterventionsFromDocumentResponseHandler.Input input = new InterventionsFromDocumentResponseHandler.Input();
		input.setResearchPaper(documentContent);
		input.setAmr(modelContent);

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("gollm:interventions_from_document");
		req.setInput(input);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendAmrToMmtRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("mira/problem.json");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.MIRA);
		req.setScript("mira_task:amr_to_mmt");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendGenerateModelLatexRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("mira/problem.json");
		final String content = new String(Files.readAllBytes(resource.getFile().toPath()));

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.MIRA);
		req.setScript("mira_task:generate_model_latex");
		req.setInput(content.getBytes());

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendLatexToAMRRequest() throws Exception {
		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.MIRA);
		req.setScript("mira_task:latex_to_amr");

		String input =
			"[ \"\\\\frac{d S(t)}{d t} = - b_q S(t) I(t)\",  \"\\\\frac{d E(t)}{d t} = b_q S(t) I(t) - r_X E(t)\",  \"\\\\frac{d I(t)}{d t} = r_X E(t) - g_p_q I(t)\",  \"\\\\frac{d R(t)}{d t} = g_p_q I(t)\"]";
		req.setInput(input.getBytes());
		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSendTextExtractionRequest() throws Exception {
		final ClassPathResource resource = new ClassPathResource("equation/SIDARTHE paper.pdf");
		final byte[] content = Files.readAllBytes(resource.getFile().toPath());

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.TEXT_EXTRACTION);
		req.setScript(ExtractTextResponseHandler.NAME);
		req.setInput(content);

		final TaskResponse resp = taskService.runTaskSync(req);

		log.info(new String(resp.getOutput()));
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCacheSuccess() throws Exception {
		final int TIMEOUT_SECONDS = 20;

		final byte[] input = "{\"input\":\"This is my input string\",\"include_progress\":true}".getBytes();

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("echo.py");
		req.setInput(input);

		final TaskFuture future1 = taskService.runTaskAsync(req);
		Assertions.assertEquals(TaskStatus.SUCCESS, future1.getFinal(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatus());

		// next request should pull the successful response from cache
		final long start = System.currentTimeMillis();
		final TaskFuture future2 = taskService.runTaskAsync(req);
		Assertions.assertEquals(TaskStatus.SUCCESS, future2.getFinal(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatus());

		Assertions.assertTrue(System.currentTimeMillis() - start < 1000);
	}

	// @Test
	@WithUserDetails(MockUser.URSULA)
	public void testItDoesNotCacheFailureButCacheSuccessAfter() throws Exception {
		final int TIMEOUT_SECONDS = 20;

		final byte[] input = "{\"input\":\"This is my input string\"},\"include_progress\":true".getBytes();

		final TaskRequest req = new TaskRequest();
		req.setType(TaskType.GOLLM);
		req.setScript("echo.py");
		req.setInput(input);

		final TaskFuture future1 = taskService.runTaskAsync(req);
		taskService.cancelTask(req.getType(), future1.getId());
		Assertions.assertEquals(TaskStatus.CANCELLED, future1.getFinal(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatus());

		// next request should not pull the cancelled response from cache
		final TaskFuture future2 = taskService.runTaskAsync(req);
		Assertions.assertEquals(TaskStatus.SUCCESS, future2.getFinal(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatus());
		Assertions.assertNotEquals(future1.getId(), future2.getId());

		// next request should pull the successful response from cache
		final TaskFuture future3 = taskService.runTaskAsync(req);
		Assertions.assertEquals(TaskStatus.SUCCESS, future3.getFinal(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStatus());
		Assertions.assertEquals(future2.getId(), future3.getId());
	}
}
