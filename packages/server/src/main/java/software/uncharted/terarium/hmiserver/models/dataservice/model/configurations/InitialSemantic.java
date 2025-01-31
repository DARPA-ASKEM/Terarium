package software.uncharted.terarium.hmiserver.models.dataservice.model.configurations;

import com.fasterxml.jackson.annotation.JsonBackReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.uncharted.terarium.hmiserver.annotations.TSIgnore;
import software.uncharted.terarium.hmiserver.annotations.TSModel;

@EqualsAndHashCode(callSuper = true)
@Data
@TSModel
@Entity
public class InitialSemantic extends Semantic {

	@Serial
	private static final long serialVersionUID = 8134786856949924125L;

	@Column(columnDefinition = "text")
	private String target;

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
	public InitialSemantic clone() {
		final InitialSemantic clone = new InitialSemantic();
		super.cloneSuperFields(clone);
		clone.target = this.target;
		clone.expression = this.expression;
		clone.expressionMathml = this.expressionMathml;
		return clone;
	}

	@Override
	public void setSource(final String source) {
		super.setSource(source);
	}
}
