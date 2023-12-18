package software.uncharted.terarium.hmiserver.controller.dataservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.uncharted.terarium.hmiserver.TerariumApplicationTests;
import software.uncharted.terarium.hmiserver.configuration.MockUser;
import software.uncharted.terarium.hmiserver.models.data.concept.OntologyConcept;
import software.uncharted.terarium.hmiserver.service.data.ConceptService;

public class ConceptControllerTests extends TerariumApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ConceptService conceptService;

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanCreateConcept() throws Exception {

		final OntologyConcept concept = new OntologyConcept()
				.setCurie("something");

		mockMvc.perform(MockMvcRequestBuilders.post("/concepts")
				.with(csrf())
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(concept)))
				.andExpect(status().isCreated());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanGetConcept() throws Exception {

		OntologyConcept concept = conceptService.createConcept(new OntologyConcept()
				.setCurie("something"));

		mockMvc.perform(MockMvcRequestBuilders.get("/concepts/" + concept.getId())
				.with(csrf()))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanSearchConcepts() throws Exception {

		final OntologyConcept concept0 = new OntologyConcept()
				.setCurie("something");

		final OntologyConcept concept1 = new OntologyConcept()
				.setCurie("something-else");

		final OntologyConcept concept2 = new OntologyConcept()
				.setCurie("another-curie");

		conceptService.createConcept(concept0);
		conceptService.createConcept(concept1);
		conceptService.createConcept(concept2);

		mockMvc.perform(MockMvcRequestBuilders.get("/concepts")
				.param("curie", "something")
				.with(csrf()))
				.andExpect(status().isOk())
				.andReturn();
	}

	@Test
	@WithUserDetails(MockUser.URSULA)
	public void testItCanDeleteConcept() throws Exception {
		OntologyConcept concept = conceptService.createConcept(new OntologyConcept()
				.setCurie("something"));

		mockMvc.perform(MockMvcRequestBuilders.delete("/concepts/" + concept.getId())
				.with(csrf()))
				.andExpect(status().isOk());

		Assertions.assertTrue(conceptService.getConcept(concept.getId()).isEmpty());
	}

}
