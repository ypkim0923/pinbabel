package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationScore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@Entity
@Table(name = "evaluation_run")
@SecondaryAdapter
class EvaluationRunJpaEntity {

	@Id
	@Column(name = "evaluation_run_id", nullable = false, length = 36)
	private String evaluationRunId;
	@Column(name = "dataset_id", nullable = false, length = 100)
	private String datasetId;
	@Column(name = "dataset_version", nullable = false)
	private int datasetVersion;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "started_at", nullable = false)
	private Instant startedAt;
	@Column(name = "completed_at", nullable = false)
	private Instant completedAt;
	@Column(name = "case_count", nullable = false)
	private int caseCount;
	@Column(name = "completed_case_count", nullable = false)
	private int completedCaseCount;
	@Column(name = "exact_match_case_count", nullable = false)
	private int exactMatchCaseCount;
	@Column(name = "true_positive_count", nullable = false)
	private int truePositiveCount;
	@Column(name = "false_positive_count", nullable = false)
	private int falsePositiveCount;
	@Column(name = "false_negative_count", nullable = false)
	private int falseNegativeCount;
	@Column(name = "correct_sentiment_count", nullable = false)
	private int correctSentimentCount;
	@Column(name = "expected_evidence_count", nullable = false)
	private int expectedEvidenceCount;
	@Column(name = "matched_evidence_count", nullable = false)
	private int matchedEvidenceCount;
	@Column(name = "result_schema_version", nullable = false)
	private int resultSchemaVersion;
	@Lob
	@Column(name = "case_results_json", nullable = false)
	private String caseResultsJson;

	protected EvaluationRunJpaEntity() {
	}

	static EvaluationRunJpaEntity from(EvaluationRun run, String caseResultsJson) {
		var entity = new EvaluationRunJpaEntity();
		var score = run.score();
		entity.evaluationRunId = run.id().value();
		entity.datasetId = run.datasetId();
		entity.datasetVersion = run.datasetVersion();
		entity.createdAt = run.createdAt();
		entity.startedAt = run.startedAt();
		entity.completedAt = run.completedAt();
		entity.caseCount = score.caseCount();
		entity.completedCaseCount = score.completedCaseCount();
		entity.exactMatchCaseCount = score.exactMatchCaseCount();
		entity.truePositiveCount = score.truePositiveCount();
		entity.falsePositiveCount = score.falsePositiveCount();
		entity.falseNegativeCount = score.falseNegativeCount();
		entity.correctSentimentCount = score.correctSentimentCount();
		entity.expectedEvidenceCount = score.expectedEvidenceCount();
		entity.matchedEvidenceCount = score.matchedEvidenceCount();
		entity.resultSchemaVersion = EvaluationCaseResultsJsonCodec.SCHEMA_VERSION;
		entity.caseResultsJson = caseResultsJson;
		return entity;
	}

	EvaluationRun toDomain(List<com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationCaseResult> results) {
		return new EvaluationRun(
			new EvaluationRunId(evaluationRunId), datasetId, datasetVersion, createdAt, startedAt, completedAt,
			results,
			new EvaluationScore(
				caseCount, completedCaseCount, exactMatchCaseCount, truePositiveCount, falsePositiveCount,
				falseNegativeCount, correctSentimentCount, expectedEvidenceCount, matchedEvidenceCount
			)
		);
	}

	String evaluationRunId() { return evaluationRunId; }
	Instant createdAt() { return createdAt; }
	Integer resultSchemaVersion() { return resultSchemaVersion; }
	String caseResultsJson() { return caseResultsJson; }
}
