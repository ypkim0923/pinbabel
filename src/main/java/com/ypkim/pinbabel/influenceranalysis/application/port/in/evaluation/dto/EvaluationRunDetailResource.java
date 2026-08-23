package com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun;
import java.time.Instant;
import java.util.List;

public record EvaluationRunDetailResource(
	String evaluationRunId,
	String datasetId,
	int datasetVersion,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	long durationMs,
	int caseCount,
	int completedCaseCount,
	int exactMatchCaseCount,
	double instrumentPrecision,
	double instrumentRecall,
	double instrumentF1,
	double sentimentAccuracy,
	double evidenceRecall,
	List<EvaluationCaseResultResource> cases
) {

	public EvaluationRunDetailResource {
		cases = List.copyOf(cases);
	}

	public static EvaluationRunDetailResource from(EvaluationRun run) {
		var score = run.score();
		return new EvaluationRunDetailResource(
			run.id().value(), run.datasetId(), run.datasetVersion(), run.createdAt(), run.startedAt(),
			run.completedAt(), run.durationMs(), score.caseCount(), score.completedCaseCount(),
			score.exactMatchCaseCount(), score.instrumentPrecision(), score.instrumentRecall(), score.instrumentF1(),
			score.sentimentAccuracy(), score.evidenceRecall(),
			run.caseResults().stream().map(EvaluationCaseResultResource::from).toList()
		);
	}

	public EvaluationRunSummaryResource toSummary() {
		return new EvaluationRunSummaryResource(
			evaluationRunId, datasetId, datasetVersion, createdAt, durationMs, caseCount,
			completedCaseCount, exactMatchCaseCount, instrumentF1, sentimentAccuracy, evidenceRecall
		);
	}
}
