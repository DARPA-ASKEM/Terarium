package software.uncharted.terarium.hmiserver.models.petrinet;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;

public class SpeciesJsonDeserializer extends JsonDeserializer<Species> {
	/*
		{
			"sname": "S",
			"mira_ids": "[('identity', 'ido:0000514'), ('identity', 'ido:0000511')]",
			"mira_context": [('identity', 'ido:0000514')]",
		}
	 */

	@Override
	public Species deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException, JacksonException {
		JsonNode node = p.getCodec().readTree(p);
		final Species species = new Species();

		// Set the properties
		species.setName(node.get("sname").asText());
		species.setMiraIds(new ArrayList<>());
		species.setMiraContext(new ArrayList<>());

		final String[] mira_ids = node.get("mira_ids").asText().split("\\), \\(");
		for (String ontology : mira_ids) {
			species.getMiraIds().add(new Ontology(ontology));
		}

		final String[] mira_context = node.get("mira_context").asText().split("\\), \\(");
		for (String ontology : mira_context) {
			species.getMiraContext().add(new Ontology(ontology));
		}

		return species;
	}
}
