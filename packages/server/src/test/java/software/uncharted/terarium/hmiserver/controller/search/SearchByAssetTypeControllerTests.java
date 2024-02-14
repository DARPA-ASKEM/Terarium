package software.uncharted.terarium.hmiserver.controller.search;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;

@Slf4j
public class SearchByAssetTypeControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	private static final String TEST_INDEX = "tds_document_tera_2.0";
	private static final String TEST_ASSET = "DOCUMENT";

	@Test
	@WithUserDetails(MockUser.ADAM)
	public void testKnnSearch() throws Exception {

		// Test that we get a 404 if we provide a project id that doesn't exist
		MvcResult res = mockMvc.perform(MockMvcRequestBuilders.get("/search-by-asset-type/" + TEST_ASSET)
				.param("text", "Was COVID-19 invented by aliens?")
				.param("index", TEST_INDEX) // index override
				.with(csrf()))
				.andExpect(status().isOk())
				.andReturn();

		List<JsonNode> docs = objectMapper.readValue(res.getResponse().getContentAsString(),
				new TypeReference<List<JsonNode>>() {
				});

		for (JsonNode doc : docs) {
			log.info("doc: {}", doc);
		}
	}

}
