package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.EvaluateGoldenDatasetUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.QueryEvaluationRunsUseCase;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Profile("fixture & cli")
@PrimaryAdapter
public class PinbabelEvaluationShellCommands {

	private static final Logger log = LoggerFactory.getLogger(PinbabelEvaluationShellCommands.class);
	private final EvaluateGoldenDatasetUseCase evaluateUseCase;
	private final QueryEvaluationRunsUseCase queryUseCase;
	private final PinbabelCliRenderer renderer;

	public PinbabelEvaluationShellCommands(
		EvaluateGoldenDatasetUseCase evaluateUseCase,
		QueryEvaluationRunsUseCase queryUseCase,
		PinbabelCliRenderer renderer
	) {
		this.evaluateUseCase = evaluateUseCase;
		this.queryUseCase = queryUseCase;
		this.renderer = renderer;
	}

	@ShellMethod(key = "pinbabel-evaluate", value = "Evaluate the current model and prompts against the golden dataset")
	public String evaluate() {
		try {
			return renderer.renderEvaluation(evaluateUseCase.evaluate());
		}
		catch (RuntimeException exception) {
			log.warn("Golden dataset evaluation failed");
			return "Golden Dataset 평가를 실행할 수 없습니다.";
		}
	}

	@ShellMethod(key = "pinbabel-evaluations", value = "Show the latest 20 golden dataset evaluation runs")
	public String recentEvaluations() {
		try {
			return renderer.renderRecentEvaluations(queryUseCase.recentRuns());
		}
		catch (RuntimeException exception) {
			log.warn("Evaluation run list query failed");
			return "평가 실행 기록을 조회할 수 없습니다.";
		}
	}

	@ShellMethod(key = "pinbabel-evaluation", value = "Show a golden dataset evaluation run")
	public String evaluation(
		@ShellOption(value = "--id", help = "Evaluation run identifier") String runId
	) {
		final EvaluationRunId id;
		try {
			id = new EvaluationRunId(runId);
		}
		catch (RuntimeException exception) {
			return "올바른 evaluationRunId를 입력해 주세요.";
		}
		try {
			return queryUseCase.findRun(id)
				.map(renderer::renderEvaluation)
				.orElse("해당 평가 실행 기록을 찾을 수 없습니다.");
		}
		catch (RuntimeException exception) {
			log.warn("Evaluation run detail query failed: evaluationRunId={}", id.value());
			return "평가 실행 기록을 조회할 수 없습니다.";
		}
	}
}
