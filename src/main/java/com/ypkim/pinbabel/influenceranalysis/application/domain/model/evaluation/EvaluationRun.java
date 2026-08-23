package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record EvaluationRun(
	@Identity EvaluationRunId id,
	String datasetId,
	int datasetVersion,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	List<EvaluationCaseResult> caseResults,
	EvaluationScore score
) {

	public EvaluationRun {
		caseResults = List.copyOf(caseResults);
	}

	public static EvaluationRun completed(
		EvaluationRunId id,
		GoldenDataset dataset,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		List<EvaluationCaseResult> results
	) {
		return new EvaluationRun(
			id, dataset.datasetId(), dataset.version(), createdAt, startedAt, completedAt,
			results, EvaluationScore.summarize(results)
		);
	}

	public long durationMs() {
		return Duration.between(startedAt, completedAt).toMillis();
	}
}
