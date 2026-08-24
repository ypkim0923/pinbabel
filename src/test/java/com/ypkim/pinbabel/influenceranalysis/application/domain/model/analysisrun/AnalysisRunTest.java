package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisRunTest {

	private static final Instant CREATED_AT = Instant.parse("2026-08-24T01:00:00Z");
	private static final AnalysisRunId RUN_ID = new AnalysisRunId("0198d1bb-99e0-7000-8000-000000000001");
	private static final AnalysisCorrelationId CORRELATION_ID =
		new AnalysisCorrelationId("0298d1bb-99e0-7000-8000-000000000001");

	@Test
	void requiresCorrelationIdentifier() {
		assertThatThrownBy(() -> AnalysisRun.create(RUN_ID, null, CREATED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(AnalysisRun.create(RUN_ID, CORRELATION_ID, CREATED_AT).correlationId())
			.isEqualTo(CORRELATION_ID);
	}

	@Test
	void completesRunningAnalysisAndCalculatesDuration() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);

		run.start(CREATED_AT.plusSeconds(1));
		run.attachEmbabelProcess("process-1");
		run.recordMetrics(new AnalysisRunMetrics(12L, 7L, new BigDecimal("0.0012"), List.of("gemini", "gemini")));
		run.complete(CREATED_AT.plusSeconds(4), "ANALYSIS_COMPLETED", "Analysis completed");

		assertThat(run.status()).isEqualTo(AnalysisRunStatus.COMPLETED);
		assertThat(run.durationMs()).isEqualTo(3_000L);
		assertThat(run.embabelProcessId()).isEqualTo("process-1");
		assertThat(run.metrics().models()).containsExactly("gemini");
	}

	@Test
	void rejectsBeforeEmbabelWithoutStartedTime() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);

		run.reject(CREATED_AT.plusMillis(25), "INPUT_REQUIRED", "분석 요청을 입력해 주세요.");

		assertThat(run.status()).isEqualTo(AnalysisRunStatus.REJECTED);
		assertThat(run.startedAt()).isNull();
		assertThat(run.durationMs()).isEqualTo(25L);
	}

	@Test
	void rejectsSemanticRefusalAfterEmbabelStarted() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);
		run.start(CREATED_AT.plusSeconds(1));

		run.reject(CREATED_AT.plusSeconds(2), "OUT_OF_SCOPE", "주식 분석 범위 밖의 요청입니다.");

		assertThat(run.status()).isEqualTo(AnalysisRunStatus.REJECTED);
		assertThat(run.durationMs()).isEqualTo(1_000L);
	}

	@Test
	void failsRunningAnalysis() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);
		run.start(CREATED_AT.plusSeconds(1));

		run.fail(CREATED_AT.plusSeconds(3), "AGENT_EXECUTION_FAILED", "Agent execution failed");

		assertThat(run.status()).isEqualTo(AnalysisRunStatus.FAILED);
	}

	@Test
	void degradesTraceWithoutChangingBusinessStatus() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);
		run.start(CREATED_AT.plusSeconds(1));
		run.complete(CREATED_AT.plusSeconds(2), "ANALYSIS_COMPLETED", "Analysis completed");

		run.degradeTrace("TRACE_STORAGE_UNAVAILABLE");

		assertThat(run.status()).isEqualTo(AnalysisRunStatus.COMPLETED);
		assertThat(run.traceAvailable()).isFalse();
		assertThat(run.warningCode()).isEqualTo("TRACE_STORAGE_UNAVAILABLE");
	}

	@Test
	void preventsTransitionAfterTerminalState() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);
		run.reject(CREATED_AT.plusSeconds(1), "INPUT_REQUIRED", "Rejected");

		assertThatThrownBy(() -> run.start(CREATED_AT.plusSeconds(2)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void preventsCompletionBeforeStart() {
		var run = AnalysisRun.create(RUN_ID, CREATED_AT);
		run.start(CREATED_AT.plusSeconds(2));

		assertThatThrownBy(() -> run.complete(CREATED_AT.plusSeconds(1), "DONE", "Done"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void preservesUnavailableMetricsAsNull() {
		assertThat(AnalysisRunMetrics.EMPTY.promptTokens()).isNull();
		assertThat(AnalysisRunMetrics.EMPTY.completionTokens()).isNull();
		assertThat(AnalysisRunMetrics.EMPTY.costUsd()).isNull();
	}
}
