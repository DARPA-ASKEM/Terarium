package software.uncharted.terarium.esingest.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.experimental.Accessors;

@Configuration
@ConfigurationProperties(prefix = "terarium.elasticsearch")
@Data
@Accessors(chain = true)
public class ElasticsearchConfiguration {
	String url;

	@Value("${terarium.elasticsearch.auth_enabled:false}")
	boolean authEnabled;

	String username;

	String password;

	Index index;

	public record Index(
			String prefix,
			String suffix,
			String epidemiologyRoot) {
	}

	public String getIndex(String root) {
		return String.join("_", index.prefix, root, index.suffix);
	}
}
