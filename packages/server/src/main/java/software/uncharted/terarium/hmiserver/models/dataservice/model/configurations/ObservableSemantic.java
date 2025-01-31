package software.uncharted.terarium.hmiserver.models.dataservice.model.configurations;

import com.fasterxml.jackson.annotation.JsonBackReference;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@EqualsAndHashCode(callSuper = true)
@Data
@TSModel
@Entity
public class ObservableSemantic extends Semantic {

	private String referenceId;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	private List<String> states;

	@Column(columnDefinition = "text")
	private String expression;

	@Column(columnDefinition = "text")
	private String expressionMathml;

	@ManyToOne
	@JsonBackReference
	@Schema(hidden = true)
	@TSIgnore
	@NotNull
	private ModelConfiguration modelConfiguration;

	@Override
	public ObservableSemantic clone() {
		final ObservableSemantic observableSemantic = new ObservableSemantic();
		super.cloneSuperFields(observableSemantic);
		observableSemantic.referenceId = this.referenceId;
		if (this.states != null) {
			observableSemantic.states = new ArrayList<>();
			observableSemantic.states.addAll(this.states);
		}
		observableSemantic.expression = this.expression;
		observableSemantic.expressionMathml = this.expressionMathml;
		return observableSemantic;
	}
}
