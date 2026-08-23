package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record GoldenEvaluationCase(String caseId, String instruction, List<ExpectedInstrument> expectedInstruments) {

	public GoldenEvaluationCase {
		if (caseId == null || caseId.isBlank()) {
			throw new IllegalArgumentException("Golden case identifier is required");
		}
		if (instruction == null || instruction.isBlank()) {
			throw new IllegalArgumentException("Golden case instruction is required");
		}
		if (expectedInstruments == null) {
			throw new IllegalArgumentException("Golden case expectations are required");
		}
		expectedInstruments = List.copyOf(expectedInstruments);
		if (expectedInstruments.stream().anyMatch(expected -> expected == null)) {
			throw new IllegalArgumentException("Golden case expectation must not be null");
		}
		if (expectedInstruments.stream().map(ExpectedInstrument::instrumentId).distinct().count()
			!= expectedInstruments.size()) {
			throw new IllegalArgumentException("Expected instrument identifiers must be unique per case");
		}
	}
}
