package com.ypkim.pinbabel.influenceranalysis.application.service.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.util.List;
import java.util.Optional;
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

	private static final class RecordingStore implements AnalysisRunStore {

		private int lastLimit;

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
			return Optional.empty();
		}
	}
}
