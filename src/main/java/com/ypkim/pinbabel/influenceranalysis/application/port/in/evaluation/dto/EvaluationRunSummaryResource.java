package com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto;

import java.time.Instant;

public record EvaluationRunSummaryResource(
	String evaluationRunId,
	String datasetId,
	int datasetVersion,
	Instant createdAt,
	long durationMs,
	int caseCount,
	int completedCaseCount,
	int exactMatchCaseCount,
	double instrumentF1,
	double sentimentAccuracy,
	double evidenceRecall
) {
}
