package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisRunId(String value) {

	public AnalysisRunId {
		if (value == null) {
			throw new IllegalArgumentException("Analysis run identifier is required");
		}
		var parsed = UUID.fromString(value);
		value = parsed.toString();
	}

	public static AnalysisRunId newId() {
		return new AnalysisRunId(UUID.randomUUID().toString());
	}
}
