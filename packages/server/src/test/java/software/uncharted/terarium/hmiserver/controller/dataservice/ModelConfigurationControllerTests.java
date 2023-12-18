package software.uncharted.terarium.hmiserver.controller.dataservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.ElasticsearchConfiguration;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.dataservice.model.ModelConfiguration;
import software.uncharted.terarium.hmiserver.service.data.ModelConfigurationService;
import software.uncharted.terarium.hmiserver.service.elasticsearch.ElasticsearchService;

public class ModelConfigurationControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ModelConfigurationService modelConfigurationService;

	@Autowired
	private ElasticsearchService elasticService;

	@Autowired
	private ElasticsearchConfiguration elasticConfig;

	@BeforeEach
	public void setup() throws IOException {
		elasticService.createOrEnsureIndexIsEmpty(elasticConfig.getModelConfigurationIndex());
	}

	@AfterEach
	public void teardown() throws IOException {
		elasticService.deleteIndex(elasticConfig.getModelConfigurationIndex());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetModelConfiguration() throws Exception {

		final ModelConfiguration modelConfiguration = modelConfigurationService
				.createModelConfiguration(new ModelConfiguration()
						.setName("test-framework")
						.setModelId(UUID.randomUUID())
						.setDescription("test-desc")
						.setConfiguration(Map.of("key", "value")));

		mockMvc.perform(MockMvcRequestBuilders.get("/model_configurations/" + modelConfiguration.getId())
				.with(csrf()))
				.andExpect(status().isCreated());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateModelConfiguration() throws Exception {

		final ModelConfiguration modelConfiguration = modelConfigurationService
				.createModelConfiguration(new ModelConfiguration()
						.setName("test-framework")
						.setModelId(UUID.randomUUID())
						.setDescription("test-desc")
						.setConfiguration(Map.of("key", "value")));

		mockMvc.perform(MockMvcRequestBuilders.post("/model_configurations")
				.with(csrf())
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(modelConfiguration)))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanUpdateModelConfiguration() throws Exception {

		final ModelConfiguration modelConfiguration = modelConfigurationService
				.createModelConfiguration(new ModelConfiguration()
						.setName("test-framework")
						.setModelId(UUID.randomUUID())
						.setDescription("test-desc")
						.setConfiguration(Map.of("key", "value")));

		mockMvc.perform(MockMvcRequestBuilders.put("/model_configurations/" + modelConfiguration.getId())
				.with(csrf())
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(modelConfiguration)))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteModelConfiguration() throws Exception {

		final ModelConfiguration modelConfiguration = modelConfigurationService
				.createModelConfiguration(new ModelConfiguration()
						.setName("test-framework")
						.setModelId(UUID.randomUUID())
						.setDescription("test-desc")
						.setConfiguration(Map.of("key", "value")));

		mockMvc.perform(MockMvcRequestBuilders.delete("/model_configurations/" + modelConfiguration.getId())
				.with(csrf()))
				.andExpect(status().isOk());
	}

}
