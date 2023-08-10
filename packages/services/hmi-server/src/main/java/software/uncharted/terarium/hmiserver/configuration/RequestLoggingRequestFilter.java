package software.uncharted.terarium.hmiserver.configuration;

import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;
import software.uncharted.terarium.hmiserver.annotations.IgnoreRequestLogging;
import software.uncharted.terarium.hmiserver.services.StructuredLog;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.time.Instant;

@Provider
@Slf4j
public class RequestLoggingRequestFilter implements ContainerRequestFilter {
	public static final String REQUEST_START_TIMESTAMP_MS = "request-start-timestamp-ms";

	@Inject
	StructuredLog structuredLog;

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		final boolean shouldLog = ((PostMatchContainerRequestContext) requestContext).getResourceMethod().getMethod().getAnnotation(IgnoreRequestLogging.class) == null;
		if (shouldLog) {
			final String user = requestContext.getSecurityContext().getUserPrincipal() != null ? requestContext.getSecurityContext().getUserPrincipal().getName() : "Anonymous";
			structuredLog.log(StructuredLog.Type.REQUEST_STARTED, user,
				"uri", requestContext.getUriInfo().getPath(),
				"method", requestContext.getRequest().getMethod()
			);
			requestContext.setProperty(REQUEST_START_TIMESTAMP_MS, Instant.now().toEpochMilli());
		}
	}
}
