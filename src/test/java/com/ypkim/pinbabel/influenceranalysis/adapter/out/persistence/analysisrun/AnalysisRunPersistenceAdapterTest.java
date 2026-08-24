package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AssessmentEvidence;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentSummary;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisCorrelationId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AnalysisRunPersistenceAdapterTest {

	private final AnalysisRunStore store;
	private final JdbcClient jdbcClient;

	@Autowired
	AnalysisRunPersistenceAdapterTest(AnalysisRunStore store, JdbcClient jdbcClient) {
		this.store = store;
		this.jdbcClient = jdbcClient;
	}

	@Test
	void storesLifecycleEventsAndStructuredReport() {
		var createdAt = Instant.parse("2026-08-24T01:00:00Z");
		var correlationId = correlationId(1);
		var run = AnalysisRun.create(id(1), correlationId, createdAt);
		store.save(run, null);
		run.start(createdAt.plusSeconds(1));
		store.save(run, null);
		store.append(run.id(), event(2, "ACTION_STARTED", createdAt.plusSeconds(2)));
		store.append(run.id(), event(1, "PLAN_FORMULATED", createdAt.plusMillis(1500)));
		var report = report();
		run.complete(createdAt.plusSeconds(4), "ANALYSIS_COMPLETED", "Analysis completed");
		store.save(run, report);

		var detail = store.findById(run.id()).orElseThrow();

		assertThat(detail.status()).isEqualTo(AnalysisRunStatus.COMPLETED);
		assertThat(detail.correlationId()).isEqualTo(correlationId.value());
		assertThat(detail.durationMs()).isEqualTo(3_000L);
		assertThat(detail.report()).isEqualTo(report);
		assertThat(detail.events()).extracting(AnalysisTraceEvent::sequence).containsExactly(1L, 2L);
	}

	@Test
	void returnsLatestTwentyInDeterministicOrder() {
		var baseline = Instant.parse("2026-08-24T01:00:00Z");
		for (var index = 1; index <= 22; index++) {
			var run = AnalysisRun.create(id(index), baseline.plusSeconds(index / 2));
			run.reject(baseline.plusSeconds(index / 2 + 1), "REJECTED", "Rejected");
			store.save(run, null);
		}

		var recent = store.findLatest(20);

		assertThat(recent).hasSize(20);
		assertThat(recent.getFirst().runId()).isEqualTo(id(22).value());
		assertThat(recent.getLast().runId()).isEqualTo(id(3).value());
	}

	@Test
	void returnsEmptyForUnknownRun() {
		assertThat(store.findById(id(999))).isEmpty();
	}

	@Test
	void eventSchemaContainsOnlyAllowlistedMetadataColumns() {
		var columns = jdbcClient.sql("""
			select column_name
			from information_schema.columns
			where table_name = 'ANALYSIS_RUN_EVENT'
			""").query(String.class).list();

		assertThat(columns).containsExactlyInAnyOrder(
			"RUN_ID", "EVENT_SEQUENCE", "EVENT_TYPE", "OCCURRED_AT", "PROCESS_ID",
			"ACTION_NAME", "TOOL_NAME", "MODEL_NAME", "PROVIDER_NAME", "DURATION_MS", "SUCCESSFUL"
		);
	}

	private static AnalysisTraceEvent event(long sequence, String type, Instant at) {
		return new AnalysisTraceEvent(
			sequence, type, at, "process-1", "action", null, null, null, null, null
		);
	}

	private static InfluencerAnalysisReport report() {
		return new InfluencerAnalysisReport(
			"fixture",
			"market-voice",
			new AnalysisPeriod(
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-02T00:00:00Z"),
				ZoneId.of("UTC")
			),
			List.of(new InstrumentSummary(
				"NASDAQ:AAPL", "AAPL", "Apple Inc.", Sentiment.POSITIVE,
				1, 0, 0, 0, false, List.of("post-1")
			)),
			List.of(new AssessmentEvidence(
				"post-1", "fixture", "market-voice", Instant.parse("2026-08-01T01:00:00Z"),
				"https://example.test/post-1", "fixture", "NASDAQ:AAPL", "AAPL",
				Sentiment.POSITIVE, "A bounded source excerpt", "Positive product outlook"
			)),
			List.of("NO_POSTS"),
			"Not investment advice"
		);
	}

	private static AnalysisRunId id(int suffix) {
		return new AnalysisRunId("0198d1bb-99e0-7000-8000-%012d".formatted(suffix));
	}

	private static AnalysisCorrelationId correlationId(int suffix) {
		return new AnalysisCorrelationId("0298d1bb-99e0-7000-8000-%012d".formatted(suffix));
	}
}
