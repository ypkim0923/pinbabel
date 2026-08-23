package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.EvaluateGoldenDatasetUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.QueryEvaluationRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationCaseResultResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunSummaryResource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PinbabelEvaluationShellCommandsTest {

	private static final String RUN_ID = "0198d1bb-99e0-7000-8000-000000000001";

	@Test
	void evaluatesAndRendersScoresAndCaseCorrelation() {
		var detail = detail();
		var commands = commands(() -> detail, new StubQuery(List.of(detail.toSummary()), Optional.of(detail)));

		assertThat(commands.evaluate())
			.contains("evaluationRunId: " + RUN_ID)
			.contains("instrumentF1: 1.0000")
			.contains("sentimentAccuracy: 1.0000")
			.contains("analysisRunId: analysis-1");
		assertThat(commands.recentEvaluations()).contains("recentEvaluations: 1");
		assertThat(commands.evaluation(RUN_ID)).contains("caseId: case-1");
	}

	@Test
	void hidesFailuresAndMalformedIdentifiers() {
		EvaluateGoldenDatasetUseCase failing = () -> { throw new IllegalStateException("secret"); };
		var commands = commands(failing, new StubQuery(List.of(), Optional.empty()));

		assertThat(commands.evaluate()).isEqualTo("Golden Dataset 평가를 실행할 수 없습니다.");
		assertThat(commands.evaluation("bad-id")).isEqualTo("올바른 evaluationRunId를 입력해 주세요.");
		assertThat(commands.evaluation(RUN_ID)).isEqualTo("해당 평가 실행 기록을 찾을 수 없습니다.");
	}

	private static PinbabelEvaluationShellCommands commands(
		EvaluateGoldenDatasetUseCase evaluate,
		QueryEvaluationRunsUseCase query
	) {
		return new PinbabelEvaluationShellCommands(evaluate, query, new PinbabelCliRenderer());
	}

	private static EvaluationRunDetailResource detail() {
		var at = Instant.parse("2026-08-24T01:00:00Z");
		return new EvaluationRunDetailResource(
			RUN_ID, "dataset", 1, at, at, at.plusSeconds(1), 1000,
			1, 1, 1, 1, 1, 1, 1, 1,
			List.of(new EvaluationCaseResultResource(
				"case-1", "analysis-1", "COMPLETED", true, 1, 1, 1, List.of()
			))
		);
	}

	private record StubQuery(
		List<EvaluationRunSummaryResource> summaries,
		Optional<EvaluationRunDetailResource> detail
	) implements QueryEvaluationRunsUseCase {
		@Override public List<EvaluationRunSummaryResource> recentRuns() { return summaries; }
		@Override public Optional<EvaluationRunDetailResource> findRun(EvaluationRunId runId) { return detail; }
	}
}
