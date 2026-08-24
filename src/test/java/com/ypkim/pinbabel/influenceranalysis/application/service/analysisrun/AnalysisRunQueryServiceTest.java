package com.ypkim.pinbabel.influenceranalysis.application.service.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalysisRunQueryServiceTest {

	@Test
	void requestsTheFixedRecentRunLimit() {
		var store = new RecordingStore();
		var service = new AnalysisRunQueryService(store);

		assertThat(service.recentRuns()).isEmpty();
		assertThat(store.lastLimit).isEqualTo(QueryAnalysisRunsUseCase.RECENT_RUN_LIMIT);
	}

	@Test
	void returnsEmptyForMissingRun() {
		var service = new AnalysisRunQueryService(new RecordingStore());

		assertThat(service.findRun(AnalysisRunId.newId())).isEmpty();
	}

	@Test
	void mapsStoredDomainEventsToProtocolNeutralProgressResources() {
		var store = new RecordingStore();
		var occurredAt = Instant.parse("2026-08-24T01:00:00Z");
		store.detail = Optional.of(new StoredAnalysisRunDetail(
			"0198d1bb-99e0-7000-8000-000000000001",
			"0298d1bb-99e0-7000-8000-000000000001",
			AnalysisRunStatus.RUNNING,
			occurredAt,
			occurredAt,
			null,
			null,
			true,
			null,
			null,
			null,
			AnalysisRunMetrics.EMPTY,
			null,
			List.of(new AnalysisTraceEvent(
				1, "ACTION_STARTED", occurredAt, "private-process-id", "collect", null,
				null, "private-provider", null, null
			))
		));

		var resource = new AnalysisRunQueryService(store)
			.findRun(new AnalysisRunId("0198d1bb-99e0-7000-8000-000000000001"))
			.orElseThrow();

		assertThat(resource.correlationId()).isEqualTo("0298d1bb-99e0-7000-8000-000000000001");
		assertThat(resource.status()).isEqualTo("RUNNING");
		assertThat(resource.events()).singleElement().satisfies(event -> {
			assertThat(event.eventType()).isEqualTo("ACTION_STARTED");
			assertThat(event.actionName()).isEqualTo("collect");
		});
	}

	private static final class RecordingStore implements AnalysisRunStore {

		private int lastLimit;
		private Optional<StoredAnalysisRunDetail> detail = Optional.empty();

		@Override
		public void save(AnalysisRun run, InfluencerAnalysisReport report) {
		}

		@Override
		public void append(AnalysisRunId runId, AnalysisTraceEvent event) {
		}

		@Override
		public List<StoredAnalysisRunSummary> findLatest(int limit) {
			lastLimit = limit;
			return List.of();
		}

		@Override
		public Optional<StoredAnalysisRunDetail> findById(AnalysisRunId runId) {
			return detail;
		}
	}
}
