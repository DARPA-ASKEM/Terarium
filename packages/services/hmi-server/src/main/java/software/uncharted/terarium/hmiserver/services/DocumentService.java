package software.uncharted.terarium.hmiserver.services;

import software.uncharted.terarium.hmiserver.models.XDD.Document;
import software.uncharted.terarium.hmiserver.models.XDD.Extraction;
import software.uncharted.terarium.hmiserver.models.XDD.XDDArticlesResponseOK;
import software.uncharted.terarium.hmiserver.models.XDD.XDDExtractionsResponseOK;
import software.uncharted.terarium.hmiserver.models.XDD.XDDResponse;
import software.uncharted.terarium.hmiserver.models.XDD.XDDSearchPayload;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;

import javax.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class DocumentService {
	// XDD API URLs
	private String DOCUMENTS_BASE_URL = "https://xdd.wisc.edu/api/articles?";
	private String EXTRACTIONS_BASE_URL = "https://xdddev.chtc.io/askem/object?";

	// create a client
	HttpClient client = HttpClient.newHttpClient();

	public List<Document> getDocuments(String jsonPayload) {
		List<Document> list = new ArrayList<>();

		String url = DOCUMENTS_BASE_URL;

		if (!jsonPayload.isEmpty()) {
			try {
				XDDSearchPayload payload = new ObjectMapper()
					.readValue(jsonPayload, XDDSearchPayload.class);
				url += "doi=" + payload.doi;
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// create a request
		var request = HttpRequest.newBuilder(
					URI.create(url))
			.header("accept", "application/json")
			.build();

		// use the client to send the request
		// @NOTE: we may as well use send the request sync, but this initial implementation uses async
		var responseFuture = client.sendAsync(request, BodyHandlers.ofString());

		// We can do other things here while the request is in-flight

		// This blocks until the request is complete
		try {
			var response = responseFuture.get();

			String responseBodyStr = response.body();
			try {
				XDDResponse<XDDArticlesResponseOK> typedResponse = new ObjectMapper()
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
					.readValue(responseBodyStr, new TypeReference<XDDResponse<XDDArticlesResponseOK>>() {});

				// NOTE that if no params are provided in the search payload,
				//  then the XDD API results will not be valid (and the mapping will not fail)
				if (typedResponse.success != null && typedResponse.success.data != null) {
					for (Document doc : typedResponse.success.data) {
						list.add(doc);
					}
				}
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}

		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}

		return list;
	}

	public List<Extraction> getExtractions(String jsonPayload) {
		List<Extraction> list = new ArrayList<>();

		String url = EXTRACTIONS_BASE_URL;

		if (!jsonPayload.isEmpty()) {
			try {
				XDDSearchPayload payload = new ObjectMapper()
					.readValue(jsonPayload, XDDSearchPayload.class);
				url += "doi=" + payload.doi;
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// create a request
		var request = HttpRequest.newBuilder(
					URI.create(url))
			.header("accept", "application/json")
			.build();

		// use the client to send the request
		// @NOTE: we may as well use send the request sync, but this initial implementation uses async
		var responseFuture = client.sendAsync(request, BodyHandlers.ofString());

		// We can do other things here while the request is in-flight

		// This blocks until the request is complete
		try {
			var response = responseFuture.get();

			String responseBodyStr = response.body();
			try {
				XDDResponse<XDDExtractionsResponseOK> typedResponse = new ObjectMapper()
					// .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
					.readValue(responseBodyStr, new TypeReference<XDDResponse<XDDExtractionsResponseOK>>() {});

				// NOTE that if no params are provided in the search payload,
				//  then the XDD API results will not be valid (and the mapping will not fail)
				if (typedResponse.success != null && typedResponse.success.data != null) {
					for (Extraction ext : typedResponse.success.data) {
						list.add(ext);
					}
				}
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}

		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}

		return list;
	}
}
