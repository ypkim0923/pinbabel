package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record GoldenDataset(String datasetId, int version, List<GoldenEvaluationCase> cases) {

	public GoldenDataset {
		if (datasetId == null || datasetId.isBlank()) {
			throw new IllegalArgumentException("Golden dataset identifier is required");
		}
		if (version < 1) {
			throw new IllegalArgumentException("Golden dataset version must be positive");
		}
		if (cases == null || cases.isEmpty()) {
			throw new IllegalArgumentException("Golden dataset cases are required");
		}
		cases = List.copyOf(cases);
		if (cases.stream().anyMatch(evaluationCase -> evaluationCase == null)) {
			throw new IllegalArgumentException("Golden dataset case must not be null");
		}
		if (cases.stream().map(GoldenEvaluationCase::caseId).distinct().count() != cases.size()) {
			throw new IllegalArgumentException("Golden dataset case identifiers must be unique");
		}
	}
}
