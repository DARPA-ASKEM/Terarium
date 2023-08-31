package software.uncharted.terarium.hmiserver.service;

import lombok.RequiredArgsConstructor;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import software.uncharted.terarium.hmiserver.configuration.Config;
import software.uncharted.terarium.hmiserver.model.User;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class AdminClientService {

  private final Config config;

  private Keycloak keycloak = null;

  @PostConstruct
  public void init() {
    //todo configify
    keycloak = KeycloakBuilder.builder()
      .serverUrl(config.getKeycloak().getUrl())
      .realm(config.getKeycloak().getAdminRealm())
      .clientId(config.getKeycloak().getAdminClientId())
      .grantType(OAuth2Constants.PASSWORD)
      .username(config.getKeycloak().getAdminUsername())
      .password(config.getKeycloak().getAdminPassword())
      .build();
  }

  public User getUserFromJwt(Jwt jwt) {
    var user = User.fromJwt(jwt);
    var representation = getUserRepresentationById(user.getId());
    user.setEnabled(representation.isEnabled()); //for now.
    return user;
  }


  private UserResource getUserResource(String id) {
    return keycloak.realm("Uncharted").users().get(id);
  }

  private UserRepresentation getUserRepresentationById(String id) {
    return getUserResource(id).toRepresentation();
  }

  // Updates the user representation in keycloak based on our internal user model
  public Boolean updateUserRepresentation(User user) {
    try {
      var resource = getUserResource(user.getId());
      var representation = resource.toRepresentation();

      //update fields here
      representation.setEmail(user.getEmail());
      representation.setFirstName(user.getGivenName());
      representation.setLastName(user.getFamilyName());
      representation.setUsername(user.getUsername());
      resource.update(representation);
      return true;
    } catch (Exception e) {
      return false;
    }
  }


}
