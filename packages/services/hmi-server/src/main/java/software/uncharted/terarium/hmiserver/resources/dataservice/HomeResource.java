
package software.uncharted.terarium.hmiserver.resources.dataservice;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import software.uncharted.terarium.hmiserver.models.documentservice.RelatedDocument;
import software.uncharted.terarium.hmiserver.models.dataservice.Assets;
import software.uncharted.terarium.hmiserver.models.dataservice.DocumentAsset;
import software.uncharted.terarium.hmiserver.models.dataservice.ResourceType;
import software.uncharted.terarium.hmiserver.proxies.dataservice.ProjectProxy;
import software.uncharted.terarium.hmiserver.models.dataservice.Project;


import software.uncharted.terarium.hmiserver.models.documentservice.Document;
import software.uncharted.terarium.hmiserver.proxies.documentservice.DocumentProxy;
import software.uncharted.terarium.hmiserver.resources.documentservice.responses.XDDRelatedDocumentsResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;


@Path("/api/home")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Home Screen End Points")
@Slf4j
public class HomeResource {
	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_PAGE_SIZE = 200;

	//TODO: Fix the hard coded xdd-covid-19
	private static final String DEFAULT_DOC = "xdd-covid-19";
	@Inject
	@RestClient
	ProjectProxy projectProxy;

	@Inject
	@RestClient
	DocumentProxy documentProxy;

	@GET
	@APIResponses({
		@APIResponse(responseCode = "500", description = "An error occurred retrieving projects"),
		@APIResponse(responseCode = "204", description = "Request received successfully, but there are no projects")})
	/*
	 * 1) Get all projects
	 * 2) get all assets for each project
	 * 3) Get all related articles for the first asset of each project
	 * Return all projects + their related projects for the homepage to display
	 */
	public Response getHomePageInfo() {
		List<Project> allProjects;
		try {
			allProjects = projectProxy.getProjects(DEFAULT_PAGE_SIZE, DEFAULT_PAGE);
		} catch (RuntimeException e) {
			log.error("Unable to get projects", e);
			return Response
				.status(Response.Status.INTERNAL_SERVER_ERROR)
				.type(MediaType.APPLICATION_JSON)
				.build();
		}

		// Remove non active (soft-deleted) projects
		// TODO - this should be done in the data-service
		allProjects = allProjects
			.stream()
			.filter(Project::getActive)
			.toList();

		//Get project's related documents and add them to the project.
		//Currently related documents is really stupid. It just grabs the first publication in the project and will get related documents of that publication.
		//TODO: Make this smarter than grabbing first publication and then its related
		for (Project project : allProjects) {
			Assets assets;
			try {
				assets = projectProxy.getAssets(project.getProjectID(), Arrays.asList(ResourceType.Type.PUBLICATIONS.type, ResourceType.Type.MODELS.type, ResourceType.Type.DATASETS.type));
				project.setAssets(assets);
			} catch (RuntimeException e) {
				log.warn("Unable to access publications for project " + project.getProjectID(), e);
				continue;
			}

			List<DocumentAsset> currentProjectPublications = assets.getPublications();

			if (currentProjectPublications.size() > 0) {

				XDDRelatedDocumentsResponse relatedDocumentResponse = documentProxy.getRelatedDocuments(DEFAULT_DOC, currentProjectPublications.get(0).getXddUri());
				List<Document> relatedDocuments = new ArrayList<>();
				for (RelatedDocument relatedDocument : relatedDocumentResponse.getData()) {
					relatedDocuments.add(relatedDocument.getDocument());
				}

				project.setRelatedDocuments(relatedDocuments);
			}
		}
		if (allProjects.isEmpty()) {
			return Response
				.noContent()
				.build();
		} else {
			return Response
				.status(Response.Status.OK)
				.entity(allProjects)
				.type(MediaType.APPLICATION_JSON)
				.build();
		}

	}

}
