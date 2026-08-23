package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PinbabelRunShellCommandsTest {

	private static final String RUN_ID = "0198d1bb-99e0-7000-8000-000000000001";
	private static final Instant CREATED_AT = Instant.parse("2026-08-24T01:00:00Z");

	@Test
	void rendersRecentRuns() {
		var commands = commands(new StubQueryUseCase(
			List.of(new AnalysisRunSummaryResource(
				RUN_ID, AnalysisRunStatus.COMPLETED, CREATED_AT, CREATED_AT, 1200L, true
			)),
			Optional.empty()
		));

		assertThat(commands.recentRuns())
			.contains("recentRuns: 1")
			.contains("runId: " + RUN_ID)
			.contains("status: COMPLETED");
	}

	@Test
	void rendersDetailEventsInProvidedOrder() {
		var detail = new AnalysisRunDetailResource(
			RUN_ID,
			AnalysisRunStatus.FAILED,
			CREATED_AT,
			CREATED_AT,
			CREATED_AT.plusSeconds(1),
			1000L,
			true,
			null,
			"AGENT_EXECUTION_FAILED",
			"Agent execution failed",
			new AnalysisRunMetrics(12L, 7L, new BigDecimal("0.0012"), List.of("gemini-3.6-flash")),
			null,
			List.of(
				event(1, "LLM_REQUESTED"),
				event(2, "PROCESS_FAILED")
			)
		);

		var result = commands(new StubQueryUseCase(List.of(), Optional.of(detail))).run(RUN_ID);

		assertThat(result)
			.contains("promptTokens: 12")
			.contains("completionTokens: 7")
			.contains("costUsd: 0.0012")
			.contains("models: gemini-3.6-flash")
			.containsSubsequence("#1", "LLM_REQUESTED", "#2", "PROCESS_FAILED");
	}

	@Test
	void hidesInternalsForMalformedAndMissingRunIds() {
		var commands = commands(new StubQueryUseCase(List.of(), Optional.empty()));

		assertThat(commands.run("not-a-run-id")).isEqualTo("올바른 runId를 입력해 주세요.");
		assertThat(commands.run(RUN_ID)).isEqualTo("해당 실행 기록을 찾을 수 없습니다.");
	}

	@Test
	void returnsSafeMessageWhenQueryFails() {
		var commands = commands(new QueryAnalysisRunsUseCase() {
			@Override
			public List<AnalysisRunSummaryResource> recentRuns() {
				throw new IllegalStateException("jdbc:h2:secret");
			}

			@Override
			public Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId) {
				throw new IllegalStateException("jdbc:h2:secret");
			}
		});

		assertThat(commands.recentRuns()).isEqualTo("실행 기록을 조회할 수 없습니다.");
		assertThat(commands.run(RUN_ID)).isEqualTo("실행 기록을 조회할 수 없습니다.");
	}

	private static PinbabelRunShellCommands commands(QueryAnalysisRunsUseCase useCase) {
		return new PinbabelRunShellCommands(useCase, new PinbabelCliRenderer());
	}

	private static AnalysisTraceEvent event(long sequence, String type) {
		return new AnalysisTraceEvent(sequence, type, CREATED_AT, "process-1", null, null, null, null, null, null);
	}

	private record StubQueryUseCase(
		List<AnalysisRunSummaryResource> summaries,
		Optional<AnalysisRunDetailResource> detail
	) implements QueryAnalysisRunsUseCase {
		@Override
		public List<AnalysisRunSummaryResource> recentRuns() {
			return summaries;
		}

		@Override
		public Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId) {
			return detail;
		}
	}
}
