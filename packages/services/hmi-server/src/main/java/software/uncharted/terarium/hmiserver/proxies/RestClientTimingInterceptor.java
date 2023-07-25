package software.uncharted.terarium.hmiserver.proxies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.identity.SecurityIdentity;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.hmiserver.annotations.LogRestClientTime;
import software.uncharted.terarium.hmiserver.entities.Event;
import software.uncharted.terarium.hmiserver.models.EventType;
import software.uncharted.terarium.hmiserver.services.EventService;
import software.uncharted.terarium.hmiserver.services.StructuredLog;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.util.UUID;

@Interceptor
@LogRestClientTime
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
@Slf4j
public class RestClientTimingInterceptor {
	@Inject
	SecurityIdentity securityIdentity;

	@Inject
	StructuredLog structuredLog;

	@Inject
	ObjectMapper mapper;

	@Inject
	EventService eventService;

	/**
	 * Create a structured log of the proxy being called.  Logs:
	 * 	- PROXY_REQUEST identifier
	 * 	- The username of the user making the proxy, Anonymous if it is done on behalf of the system itself
	 * 	- The name of the proxy method (in Java)
	 * 	- The name of the parent class (the actual rest client)
	 * 	- The rest path being executed
	 * 	- The method type (GET, POST, etc)
	 * 	- An optional group that can be specified on the annotation, if not provided, defaults to the package name for grouping
	 * 	- The time in ms the call took
	 * @param context	Invocation context
	 * @return				the next interceptor in the chain
	 * @throws Exception
	 */
	@AroundInvoke
	public Object measureInvocationTime(InvocationContext context) throws Exception {
		long startTime = System.currentTimeMillis();
		try {
			return context.proceed();
		} finally {
			final long endTime = System.currentTimeMillis();
			final long executionTime = endTime - startTime;
			final String user = securityIdentity.getPrincipal() != null ? securityIdentity.getPrincipal().getName() : null;
			structuredLog.log(StructuredLog.Type.PROXY_REQUEST, user,
				"method", context.getMethod().getName(),
				"class", context.getMethod().getDeclaringClass().getName(),
				"path", getPath(context),
				"type", getType(context),
				"group", context.getMethod().getAnnotation(LogRestClientTime.class).group().equals("") ? parentPackage(context) : context.getMethod().getAnnotation(LogRestClientTime.class).group(),
				"duration", executionTime);

			final Event proxyEvent = new Event()
				.setId(UUID.randomUUID().toString())
				.setTimestampMillis(System.currentTimeMillis())
				.setUsername(user)
				.setType(EventType.PROXY_TIMING);

			final ObjectNode node = mapper.createObjectNode()
				.put("method", context.getMethod().getName())
				.put("class", context.getMethod().getDeclaringClass().getName())
				.put("path", getPath(context))
				.put("type", getType(context))
				.put("group", context.getMethod().getAnnotation(LogRestClientTime.class).group().equals("") ? parentPackage(context) : context.getMethod().getAnnotation(LogRestClientTime.class).group())
				.put("duration", executionTime);
			proxyEvent.setValue(node.toString());
			eventService.persistEvent(proxyEvent);
		}
	}

	/**
	 * Get the method type of the rest call being made
	 * @param context	Invocation context
	 * @return				The method type (GET, POST, etc)
	 */
	private String getType(InvocationContext context) {
		if (context.getMethod().getAnnotation(jakarta.ws.rs.GET.class) != null) {
			return "GET";
		} else if (context.getMethod().getAnnotation(jakarta.ws.rs.POST.class) != null) {
			return "POST";
		} else if (context.getMethod().getAnnotation(jakarta.ws.rs.PUT.class) != null) {
			return "PUT";
		} else if (context.getMethod().getAnnotation(jakarta.ws.rs.DELETE.class) != null) {
			return "DELETE";
		} else if (context.getMethod().getAnnotation(jakarta.ws.rs.HEAD.class) != null) {
			return "HEAD";
		} else if (context.getMethod().getAnnotation(jakarta.ws.rs.OPTIONS.class) != null) {
			return "OPTIONS";
		} else if (context.getMethod().getAnnotation(jakarta.ws.rs.PATCH.class) != null) {
			return "PATCH";
		}
		return null;
	}

	/**
	 * Gets the @Path annotation for the method being called prefixed with the parent class
	 * @param context	Invocation context
	 * @return
	 */
	private String getPath(InvocationContext context) {
		String parentPath = getPath(context.getMethod().getDeclaringClass());
		String methodPath = getPath(context.getMethod());
		if (!parentPath.equals("")) {
			return parentPath + methodPath;
		}
		return methodPath;
	}

	/**
	 * Gets the @Path annotation for the class
	 * @param clazz	The class to get the path for
	 * @return			The value of the annotation or an empty string if it doesn't exist
	 */
	private String getPath(Class<?> clazz) {
		if (clazz.getAnnotation(jakarta.ws.rs.Path.class) != null) {
			return clazz.getAnnotation(jakarta.ws.rs.Path.class).value();
		}
		return "";
	}

	/**
	 * Gets the @Path annotation for the method
	 * @param method	The method to get the path for
	 * @return				The value of the annotation or an empty string if it doesn't exist
	 */
	private String getPath(Method method) {
		if (method.getAnnotation(jakarta.ws.rs.Path.class) != null) {
			return method.getAnnotation(jakarta.ws.rs.Path.class).value();
		}
		return "";
	}

	/**
	 * Gets the parent package of the class
	 * @param context	Invocation context
	 * @return				The parent package name
	 */
	private String parentPackage(InvocationContext context) {
		final String fullyQualifiedPackage = context.getMethod().getDeclaringClass().getPackage().getName();
		return fullyQualifiedPackage.split("\\.")[fullyQualifiedPackage.split("\\.").length - 1];
	}
}




