package com.ypkim.pinbabel.influenceranalysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.embabel.agent.core.AgentPlatform;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisScopePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorder;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorderFactory;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbabelInfluencerAnalysisServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T01:00:00Z"), ZoneOffset.UTC);

	@Test
	void rejectsBlankInputBeforeEmbabelAndPersistsRun() {
		var store = new RecordingStore();
		var service = service(store);

		var resource = service.analyze(new AnalyzeInfluencerPostsCommand("  "));

		assertThat(resource.runId()).isNotBlank();
		assertThat(resource.status()).isEqualTo("INCOMPLETE");
		assertThat(resource.traceAvailable()).isTrue();
		assertThat(store.saved).extracting(AnalysisRun::status)
			.containsExactly(AnalysisRunStatus.CREATED, AnalysisRunStatus.REJECTED);
	}

	@Test
	void rejectsOversizedInputBeforeEmbabel() {
		var store = new RecordingStore();

		var resource = service(store).analyze(new AnalyzeInfluencerPostsCommand(
			"x".repeat(AnalysisScopePolicy.MAX_INPUT_LENGTH + 1)
		));

		assertThat(resource.status()).isEqualTo("REFUSED");
		assertThat(store.saved.getLast().status()).isEqualTo(AnalysisRunStatus.REJECTED);
	}

	@Test
	void recordingFailureDoesNotHideRejection() {
		var store = new RecordingStore();
		store.failSaves = true;

		var resource = service(store).analyze(null);

		assertThat(resource.status()).isEqualTo("INCOMPLETE");
		assertThat(resource.traceAvailable()).isFalse();
		assertThat(resource.warnings()).containsExactly("TRACE_STORAGE_UNAVAILABLE");
	}

	@Test
	void persistsCreatedRunBeforeSchedulingAsyncExecution() {
		var store = new RecordingStore();
		var launcher = new RecordingLauncher(true);
		var service = new EmbabelInfluencerAnalysisService(
			mock(AgentPlatform.class), store, ignored -> mock(AnalysisRunFlightRecorder.class), launcher, CLOCK
		);

		var resource = service.submit(new SubmitInfluencerAnalysisCommand("NVDA 분석해줘"));

		assertThat(resource.status()).isEqualTo("CREATED");
		assertThat(resource.runId()).isNotBlank();
		assertThat(resource.correlationId()).isNotBlank();
		assertThat(store.saved).extracting(AnalysisRun::status).containsExactly(AnalysisRunStatus.CREATED);
		assertThat(launcher.execution).isNotNull();
	}

	@Test
	void rejectsAsyncExecutionWhenCapacityIsExhausted() {
		var store = new RecordingStore();
		var service = new EmbabelInfluencerAnalysisService(
			mock(AgentPlatform.class), store, ignored -> mock(AnalysisRunFlightRecorder.class),
			new RecordingLauncher(false), CLOCK
		);

		var resource = service.submit(new SubmitInfluencerAnalysisCommand("NVDA 분석해줘"));

		assertThat(resource.status()).isEqualTo("REJECTED");
		assertThat(resource.outcomeCode()).isEqualTo("EXECUTION_CAPACITY_EXCEEDED");
		assertThat(store.saved).extracting(AnalysisRun::status)
			.containsExactly(AnalysisRunStatus.CREATED, AnalysisRunStatus.REJECTED);
	}

	@Test
	void recorderCreationFailureDoesNotLeaveRunStuckRunning() {
		var store = new RecordingStore();
		var service = new EmbabelInfluencerAnalysisService(
			mock(AgentPlatform.class),
			store,
			ignored -> { throw new IllegalStateException("recorder unavailable"); },
			CLOCK
		);

		var resource = service.analyze(new AnalyzeInfluencerPostsCommand("NVDA 분석해줘"));

		assertThat(resource.status()).isEqualTo("FAILED");
		assertThat(resource.traceAvailable()).isFalse();
		assertThat(resource.warnings()).containsExactly("TRACE_STORAGE_UNAVAILABLE");
		assertThat(store.saved.getLast().status()).isEqualTo(AnalysisRunStatus.FAILED);
	}

	private static EmbabelInfluencerAnalysisService service(RecordingStore store) {
		return new EmbabelInfluencerAnalysisService(
			mock(AgentPlatform.class),
			store,
			ignored -> mock(AnalysisRunFlightRecorder.class),
			CLOCK
		);
	}

	private static final class RecordingStore implements AnalysisRunStore {

		private final List<AnalysisRun> saved = new ArrayList<>();
		private boolean failSaves;

		@Override
		public void save(AnalysisRun run, InfluencerAnalysisReport report) {
			if (failSaves) {
				throw new IllegalStateException("database unavailable");
			}
			var copy = AnalysisRun.create(run.id(), run.correlationId(), run.createdAt());
			if (run.startedAt() != null) {
				copy.start(run.startedAt());
			}
			switch (run.status()) {
				case COMPLETED -> copy.complete(run.completedAt(), run.outcomeCode(), run.outcomeSummary());
				case FAILED -> copy.fail(run.completedAt(), run.outcomeCode(), run.outcomeSummary());
				case REJECTED -> copy.reject(run.completedAt(), run.outcomeCode(), run.outcomeSummary());
				case CREATED, RUNNING -> { }
			}
			if (!run.traceAvailable()) {
				copy.degradeTrace(run.warningCode());
			}
			saved.add(copy);
		}

		@Override
		public void append(AnalysisRunId runId, AnalysisTraceEvent event) {
		}

		@Override
		public List<StoredAnalysisRunSummary> findLatest(int limit) {
			return List.of();
		}

		@Override
		public Optional<StoredAnalysisRunDetail> findById(AnalysisRunId runId) {
			return Optional.empty();
		}
	}

	private static final class RecordingLauncher implements com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisExecutionLauncher {
		private final boolean accepted;
		private Runnable execution;

		private RecordingLauncher(boolean accepted) {
			this.accepted = accepted;
		}

		@Override
		public boolean launch(AnalysisRunId runId, Runnable execution) {
			this.execution = execution;
			return accepted;
		}
	}
}
