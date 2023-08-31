package software.uncharted.terarium.hmiserver.configuration;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

import java.io.Serializable;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "pantera")
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Config {
  /**
   * The base url of the deployed application.  Eg/ http://localhost:5173 or https://myapp.uncharted.software
   */
  String baseUrl;

  /**
   * A list of unauthenticated {@link org.springframework.util.AntPathMatcher} patterns for urls that should not be
   * authenticated via Spring Security
   */
  List<String> unauthenticatedUrlPatterns;

  /**
   * Keycloak configuration
   */
  Keycloak keycloak;

  /**
   * Caching configuration
   */
  Caching caching;

  /**
   * Configuration values that are passed to the client
   */
  ClientConfig clientConfig;

  @Data
  @Accessors(chain = true)
  public static class Caching {
    /**
     * If true, clear the cache on startup.  Should be false in production environments
     */
    Boolean clearOnStartup;
  }

  @Data
  @Accessors(chain = true)
  public static class Keycloak {
    /**
     * The url of the keycloak server.  eg/ http://localhost:8081 or https://keycloak.uncharted.software
     */
    String url;
    /**
     * The realm name to use for authentication
     */
    String realm;

    /**
     * the realm where the admin-cli lives
     */
    String adminRealm;

    /**
     * The client id in keycloak
     */
    String clientId;

    /**
     * The client id for the admin connection
     */
    String adminClientId;

    /**
     * the admin username
     */
    String adminUsername;

    /**
     * the admin password
     */
    String adminPassword;
  }

  @Data
  @Accessors(chain = true)
  @TSModel
  public static class ClientConfig implements Serializable {
    /**
     * The base url of the deployed application.  Mirror of {@link Config#baseUrl}
     */
    String baseUrl;

    /**
     * If true, we will log all client-side errors to the server.  This is useful for debugging, but should be false
     */
    Boolean clientLogShippingEnabled;

    /**
     * The interval, in milliseconds, at which we will ship client-side logs to the server
     */
    Long clientLogShippingIntervalMillis;
  }
}
