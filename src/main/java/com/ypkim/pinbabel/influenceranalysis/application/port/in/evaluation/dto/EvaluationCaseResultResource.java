package com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationCaseResult;
import java.util.List;

public record EvaluationCaseResultResource(
	String caseId,
	String analysisRunId,
	String analysisStatus,
	boolean exactMatch,
	double instrumentF1,
	double sentimentAccuracy,
	double evidenceRecall,
	List<String> mismatches
) {

	public EvaluationCaseResultResource {
		mismatches = List.copyOf(mismatches);
	}

	public static EvaluationCaseResultResource from(EvaluationCaseResult result) {
		var precision = ratio(result.truePositiveCount(), result.truePositiveCount() + result.falsePositiveCount());
		var recall = ratio(result.truePositiveCount(), result.truePositiveCount() + result.falseNegativeCount());
		return new EvaluationCaseResultResource(
			result.caseId(), result.analysisRunId(), result.analysisStatus(), result.exactMatch(),
			precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall),
			ratio(result.correctSentimentCount(), result.truePositiveCount()),
			ratio(result.matchedEvidenceCount(), result.expectedEvidenceCount()),
			result.mismatches()
		);
	}

	private static double ratio(int numerator, int denominator) {
		return denominator == 0 ? 0 : (double) numerator / denominator;
	}
}
