package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record EvaluationScore(
	int caseCount,
	int completedCaseCount,
	int exactMatchCaseCount,
	int truePositiveCount,
	int falsePositiveCount,
	int falseNegativeCount,
	int correctSentimentCount,
	int expectedEvidenceCount,
	int matchedEvidenceCount
) {

	public static EvaluationScore summarize(List<EvaluationCaseResult> results) {
		return new EvaluationScore(
			results.size(),
			(int) results.stream().filter(EvaluationCaseResult::analysisCompleted).count(),
			(int) results.stream().filter(EvaluationCaseResult::exactMatch).count(),
			results.stream().mapToInt(EvaluationCaseResult::truePositiveCount).sum(),
			results.stream().mapToInt(EvaluationCaseResult::falsePositiveCount).sum(),
			results.stream().mapToInt(EvaluationCaseResult::falseNegativeCount).sum(),
			results.stream().mapToInt(EvaluationCaseResult::correctSentimentCount).sum(),
			results.stream().mapToInt(EvaluationCaseResult::expectedEvidenceCount).sum(),
			results.stream().mapToInt(EvaluationCaseResult::matchedEvidenceCount).sum()
		);
	}

	public double instrumentPrecision() {
		return ratio(truePositiveCount, truePositiveCount + falsePositiveCount);
	}

	public double instrumentRecall() {
		return ratio(truePositiveCount, truePositiveCount + falseNegativeCount);
	}

	public double instrumentF1() {
		var precision = instrumentPrecision();
		var recall = instrumentRecall();
		return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
	}

	public double sentimentAccuracy() {
		return ratio(correctSentimentCount, truePositiveCount);
	}

	public double evidenceRecall() {
		return ratio(matchedEvidenceCount, expectedEvidenceCount);
	}

	private static double ratio(int numerator, int denominator) {
		return denominator == 0 ? 0 : (double) numerator / denominator;
	}
}
