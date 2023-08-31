
package software.uncharted.terarium.hmiserver.proxies.knowledgemiddleware;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.core.MediaType;
import software.uncharted.terarium.hmiserver.exceptions.HmiResponseExceptionMapper;
import software.uncharted.terarium.hmiserver.models.extractionservice.ExtractionResponse;

import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@RegisterRestClient(configKey = "knowledge-middleware")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Knowledge Middleware")
@RegisterProvider(HmiResponseExceptionMapper.class)
public interface KnowledgeMiddlewareProxy {

	/**
	 * Retrieve the status of an extraction job
	 * @param id (String) the id of the extraction job
	 * @return the status of the extraction job
	 */
	@GET
	@Path("/status/{id}")
	Response getTaskStatus(
		@PathParam("id") String id
	);

	/**
	 * Post MathML to skema service to get AMR return
	 *
	 * @param		model (String) the id of the model
	 *
	 * Args:
	 *     mathMLPayload (List<String>): A list of MathML strings representing the functions that are
	 * 													         used to convert to AMR model (str, optional): AMR model return type.
	 * 													         Defaults to "petrinet". Options: "regnet", "petrinet".
	 *
	 * @return AMR model
	 */
	@POST
	@Path("/mathml_to_amr")
	@Consumes(MediaType.APPLICATION_JSON)
	Response postMathMLToAMR(
		@DefaultValue("petrinet") @QueryParam("model") String model,
		List<String> mathMLPayload
	);

	/**
	 * Post a PDF
	 *
	 * @param    annotateSkema (Boolean): Whether to annotate the PDF with Skema
	 * @param    annotateMIT (Boolean): Whether to annotate the PDF with AMR
	 * @param    name (String): The name of the PDF
	 * @param    description (String): The description of the PDF
	 *
	 * Args:
	 *     pdf (Object): The PDF file to upload
	 *
	 * @return extractions of the pdf
	 */
	@POST
	@Path("/pdf_extractions")
	Response postPDFExtractions(
		@QueryParam("artifact_id") String artifactId,
		@DefaultValue("true") @QueryParam("annotate_skema") Boolean annotateSkema,
		@DefaultValue("true") @QueryParam("annotate_mit") Boolean annotateMIT,
		@QueryParam("name") String name,
		@QueryParam("description") String description
	);

	/**
	 * Post a PDF to get text
	 * @param artifactId (String): The ID of the artifact to extract text from
	 * @return
	 */
	@POST
	@Path("/pdf_to_text")
	Response postPDFToText(
		@QueryParam("artifact_id") String artifactId
	);

	/**
	 * Profile a model
	 *
	 * @param		modelId (String): The ID of the model to profile
	 * @param		documentText (String): The text of the document to profile
	 *
	 * @return the profiled model
	 */
	@POST
	@Path("/profile_model/{model_id}")
	@Consumes(MediaType.APPLICATION_JSON)
	Response postProfileModel(
		@PathParam("model_id") String modelId,
		@QueryParam("paper_artifact_id") String artifactId
	);

	/**
	 * Profile a dataset
	 *
	 * @param		datasetId (String): The ID of the dataset to profile
	 * @param		documentText (String): The text of the document to profile
	 *
	 * @return the profiled dataset
	 */
	@POST
	@Path("/profile_dataset/{dataset_id}")
	@Consumes(MediaType.APPLICATION_JSON)
	Response postProfileDataset(
		@PathParam("dataset_id") String datasetId,
		@QueryParam("artifact_id") String artifactId
	);

	/**
	 * Transform source code to AMR
	 * @param 	artifactId (String): id of the code artifact
	 * @param 	name (String): the name to set on the newly created model
	 * @param 	description (String): the description to set on the newly created model
	 * @return  (ExtractionResponse)
	 */
	@POST
	@Path("/code_to_amr")
	@Consumes(MediaType.APPLICATION_JSON)
	ExtractionResponse postCodeToAMR(
		@QueryParam("artifact_id") String artifactId,
		@QueryParam("name") String name,
		@QueryParam("description") String description
	);
}
