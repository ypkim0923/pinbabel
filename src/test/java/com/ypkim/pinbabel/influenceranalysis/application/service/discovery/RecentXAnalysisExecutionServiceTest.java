package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunMetricsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.RecentXAnalysisResultStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredRecentXAnalysisResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecentXAnalysisExecutionServiceTest {

	@Test
	void submitsOnceAndPersistsACompletedArtifact() {
		var runs = new FakeRuns();
		var results = new FakeResults();
		var service = new RecentXAnalysisExecutionService(
			new CompletedAnalysis(), runs, runs, results, (runId, execution) -> { execution.run(); return true; },
			Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
		);

		var submission = service.submit("@AleaBitoReddit");
		var detail = service.findRecentRun(new AnalysisRunId(submission.runId())).orElseThrow();

		assertThat(detail.status()).isEqualTo("COMPLETED");
		assertThat(detail.result().account()).isEqualTo("@aleabitoreddit");
		assertThat(detail.result().xApiRequestBudget()).isEqualTo(2);
		assertThat(detail.result().llmCallBudget()).isEqualTo(1);
	}

	@Test
	void rejectsInvalidAccountWithoutLaunchingExternalWork() {
		var runs = new FakeRuns();
		var results = new FakeResults();
		var launches = new int[1];
		var service = new RecentXAnalysisExecutionService(
			new CompletedAnalysis(), runs, runs, results, (runId, execution) -> { launches[0]++; return true; },
			Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
		);

		var submission = service.submit("not valid!");

		assertThat(submission.status()).isEqualTo("REJECTED");
		assertThat(submission.outcomeCode()).isEqualTo("INVALID_X_ACCOUNT");
		assertThat(launches[0]).isZero();
	}

	private static final class CompletedAnalysis implements AnalyzeRecentXStockInfluencerUseCase {
		@Override
		public RecentMentionedCompaniesResource mentionedCompanies(String account) {
			return new RecentMentionedCompaniesResource(
				"COMPLETED", "done", account, 1, true, true, false, 2, 1, 2, 1, 10,
				List.of(), List.of(), "not advice"
			);
		}

		@Override
		public RecentCompanySentimentResource companySentiment(String account) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class FakeResults implements RecentXAnalysisResultStore {
		private final Map<String, StoredRecentXAnalysisResult> values = new java.util.HashMap<>();
		@Override public void save(AnalysisRunId runId, StoredRecentXAnalysisResult result) { values.put(runId.value(), result); }
		@Override public Optional<StoredRecentXAnalysisResult> findByRunId(AnalysisRunId runId) { return Optional.ofNullable(values.get(runId.value())); }
	}

	private static final class FakeRuns implements AnalysisRunStore, QueryAnalysisRunsUseCase {
		private final Map<String, AnalysisRun> values = new java.util.HashMap<>();
		@Override public void save(AnalysisRun run, InfluencerAnalysisReport report) { values.put(run.id().value(), run); }
		@Override public void append(AnalysisRunId runId, AnalysisTraceEvent event) { }
		@Override public List<StoredAnalysisRunSummary> findLatest(int limit) { return List.of(); }
		@Override public Optional<StoredAnalysisRunDetail> findById(AnalysisRunId runId) { return Optional.empty(); }
		@Override public List<AnalysisRunSummaryResource> recentRuns() { return List.of(); }
		@Override public Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId) {
			return Optional.ofNullable(values.get(runId.value())).map(run -> new AnalysisRunDetailResource(
				run.id().value(), run.correlationId().value(), run.status().name(), run.createdAt(), run.startedAt(),
				run.completedAt(), run.durationMs(), run.traceAvailable(), run.warningCode(), run.outcomeCode(),
				run.outcomeSummary(), new AnalysisRunMetricsResource(null, null, null, List.of()), null, List.of()
			));
		}
	}
}
