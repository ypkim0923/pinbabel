package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisCorrelationId(String value) {

	public AnalysisCorrelationId {
		if (value == null) {
			throw new IllegalArgumentException("Analysis correlation identifier is required");
		}
		value = UUID.fromString(value).toString();
	}

	public static AnalysisCorrelationId newId() {
		return new AnalysisCorrelationId(UUID.randomUUID().toString());
	}
}
