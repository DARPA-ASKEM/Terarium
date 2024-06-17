package software.uncharted.terarium.hmiserver.models.dataservice.model.configurations;

import com.fasterxml.jackson.annotation.JsonBackReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors
@TSModel
@Entity
public class InitialSemantic extends Semantic {

	@Column(columnDefinition = "text")
	private String target;

	@Column(columnDefinition = "text")
	private String expression;

	@Column(columnDefinition = "text")
	private String expressionMathml;

	@ManyToOne
	@JsonBackReference
	@Schema(hidden = true)
	@NotNull private ModelConfiguration modelConfiguration;

	@Override
	public InitialSemantic clone() {
		final InitialSemantic clone = new InitialSemantic();
		super.cloneSuperFields(clone);
		clone.target = this.target;
		clone.expression = this.expression;
		clone.expressionMathml = this.expressionMathml;
		return clone;
	}
}
