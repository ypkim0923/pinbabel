package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record EvaluationRunId(String value) {

	public EvaluationRunId {
		if (value == null) {
			throw new IllegalArgumentException("Evaluation run identifier is required");
		}
		value = UUID.fromString(value).toString();
	}

	public static EvaluationRunId newId() {
		return new EvaluationRunId(UUID.randomUUID().toString());
	}
}
