package software.uncharted.terarium.hmiserver.proxies.github;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "github")
public interface GithubProxy {
    @GET
	@Path("/repos/{repoOwnerAndName}/contents/{path}")
	Response getGithubRepositoryContent(
		@PathParam("repoOwnerAndName") String repoOwnerAndName,
		@PathParam("path") String path
	);
}
