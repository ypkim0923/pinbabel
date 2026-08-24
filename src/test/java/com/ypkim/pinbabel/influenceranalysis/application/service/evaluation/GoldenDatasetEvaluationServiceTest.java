package com.ypkim.pinbabel.influenceranalysis.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.ExpectedInstrument;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenDataset;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenEvaluationCase;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.GoldenDatasetEvaluator;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.EvaluationRunStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoldenDatasetEvaluationServiceTest {

	@Test
	void continuesAfterAnalysisFailureAndPersistsCorrelatedResults() {
		var cases = List.of(goldenCase("case-1"), goldenCase("case-2"));
		var store = new RecordingStore();
		var calls = new int[]{0};
		AnalyzeInfluencerPostsUseCase analyzer = command -> {
			if (calls[0]++ == 0) {
				throw new IllegalStateException("provider unavailable");
			}
			return new AnalyzeInfluencerPostsResource(
				"0198d1bb-99e0-7000-8000-000000000002",
				"0198d1bb-99e0-7000-8000-000000000003",
				"FAILED", "failed", null,
				"Not investment advice", true, List.of()
			);
		};
		var service = new GoldenDatasetEvaluationService(
			() -> new GoldenDataset("dataset", 1, cases), analyzer, store,
			Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
		);

		var result = service.evaluate();

		assertThat(result.caseCount()).isEqualTo(2);
		assertThat(result.completedCaseCount()).isZero();
		assertThat(result.cases()).extracting(item -> item.analysisStatus()).containsExactly("FAILED", "FAILED");
		assertThat(result.cases().get(1).analysisRunId()).isEqualTo("0198d1bb-99e0-7000-8000-000000000002");
		assertThat(store.saved).isNotNull();
	}

	private static GoldenEvaluationCase goldenCase(String id) {
		return new GoldenEvaluationCase(id, "analyze " + id, List.of(
			new ExpectedInstrument("NASDAQ:NVDA", Sentiment.POSITIVE, List.of("post-1"))
		));
	}

	private static final class RecordingStore implements EvaluationRunStore {
		private com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun saved;

		@Override public void save(com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun run) { saved = run; }
		@Override public List<com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun> findLatest(int limit) { return new ArrayList<>(); }
		@Override public Optional<com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun> findById(com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId runId) { return Optional.empty(); }
	}
}
