package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
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
public class PinbabelRunShellCommands {

	private static final Logger log = LoggerFactory.getLogger(PinbabelRunShellCommands.class);

	private final QueryAnalysisRunsUseCase queryUseCase;
	private final PinbabelCliRenderer renderer;

	public PinbabelRunShellCommands(QueryAnalysisRunsUseCase queryUseCase, PinbabelCliRenderer renderer) {
		this.queryUseCase = queryUseCase;
		this.renderer = renderer;
	}

	@ShellMethod(key = "pinbabel-runs", value = "Show the latest 20 analysis runs")
	public String recentRuns() {
		try {
			return renderer.renderRecentRuns(queryUseCase.recentRuns());
		}
		catch (RuntimeException exception) {
			log.warn("Analysis run list query failed");
			return "실행 기록을 조회할 수 없습니다.";
		}
	}

	@ShellMethod(key = "pinbabel-run", value = "Show an analysis run and its ordered safe events")
	public String run(
		@ShellOption(value = "--id", help = "Analysis run identifier") String runId
	) {
		final AnalysisRunId id;
		try {
			id = new AnalysisRunId(runId);
		}
		catch (RuntimeException exception) {
			log.debug("Invalid analysis run identifier");
			return "올바른 runId를 입력해 주세요.";
		}
		try {
			return queryUseCase.findRun(id)
				.map(renderer::renderRunDetail)
				.orElse("해당 실행 기록을 찾을 수 없습니다.");
		}
		catch (RuntimeException exception) {
			log.warn("Analysis run detail query failed: runId={}", id.value());
			return "실행 기록을 조회할 수 없습니다.";
		}
	}
}
