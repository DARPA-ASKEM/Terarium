package software.uncharted.terarium.hmiserver.models.petrinet;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Ontology implements Serializable {

	private final String name;
	private final String curie;
	private String title;
	private String description;
	private String link;

	public Ontology(String input) {
		// We receive Petri Net models with ontology in a non JSON format
		// i.e.  [('identity', 'ido:0000511'), ('identity', 'ido:0000514')]
		final Matcher matcher = Pattern.compile("\\(\\'(.+?)\\', \\'(.+?)\\'\\)").matcher(input);
		final boolean result = matcher.find();
		int i = matcher.groupCount();
		if (result && i > 0) {
			this.name = matcher.group(1);
			this.curie = matcher.group(2);
		} else {
			this.name = this.curie = null;
		}
	}
}
