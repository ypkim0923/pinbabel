package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record EvaluationCaseResult(
	String caseId,
	String analysisRunId,
	String analysisStatus,
	int expectedInstrumentCount,
	int actualInstrumentCount,
	int truePositiveCount,
	int falsePositiveCount,
	int falseNegativeCount,
	int correctSentimentCount,
	int expectedEvidenceCount,
	int matchedEvidenceCount,
	boolean exactMatch,
	List<String> mismatches
) {

	public EvaluationCaseResult {
		mismatches = List.copyOf(mismatches);
	}

	public boolean analysisCompleted() {
		return "COMPLETED".equals(analysisStatus);
	}
}
