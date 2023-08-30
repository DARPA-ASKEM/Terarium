package software.uncharted.pantera.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import software.uncharted.pantera.PanteraApplicationTests;
import software.uncharted.pantera.configuration.MockUser;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthoritiesTests extends PanteraApplicationTests {

  @Test
  @WithUserDetails(MockUser.ADAM)
  public void testItCanPreauthorizeRoles() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/test/authorities/hasRoleAdmin"))
      .andExpect(status().isOk());
  }

  @Test
  @WithUserDetails(MockUser.URSULA)
  public void testItReturns403WhenPreauthorizedRolesAreNotPresent() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/test/authorities/hasRoleAdmin"))
      .andExpect(status().isForbidden());
  }

  @Test
  @WithUserDetails(MockUser.ADAM)
  public void testItCanPreauthorizeAuthorities() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/test/authorities/hasCreateUsers"))
      .andExpect(status().isOk());
  }

  @Test
  @WithUserDetails(MockUser.URSULA)
  public void testItReturns403WhenPreauthorizedAuthoritiesAreNotPresent() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/test/authorities/hasCreateUsers"))
      .andExpect(status().isForbidden());
  }
}
