package com.ypkim.pinbabel.influenceranalysis.application.service;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisScopeDecision;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisScopePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorder;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorderFactory;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture & cli")
public class EmbabelInfluencerAnalysisService implements AnalyzeInfluencerPostsUseCase {
	private static final Logger log = LoggerFactory.getLogger(EmbabelInfluencerAnalysisService.class);

	private final AgentPlatform agentPlatform;
	private final AnalysisRunStore runStore;
	private final AnalysisRunFlightRecorderFactory recorderFactory;
	private final Clock clock;

	@Autowired
	public EmbabelInfluencerAnalysisService(
		AgentPlatform agentPlatform,
		AnalysisRunStore runStore,
		AnalysisRunFlightRecorderFactory recorderFactory
	) {
		this(agentPlatform, runStore, recorderFactory, Clock.systemUTC());
	}

	EmbabelInfluencerAnalysisService(
		AgentPlatform agentPlatform,
		AnalysisRunStore runStore,
		AnalysisRunFlightRecorderFactory recorderFactory,
		Clock clock
	) {
		this.agentPlatform = agentPlatform;
		this.runStore = runStore;
		this.recorderFactory = recorderFactory;
		this.clock = clock;
	}

	@Override
	public AnalyzeInfluencerPostsResource analyze(AnalyzeInfluencerPostsCommand command) {
		var run = AnalysisRun.create(AnalysisRunId.newId(), now());
		persistBestEffort(run, null);
		var instruction = command == null ? null : command.instruction();
		if (instruction == null || instruction.isBlank()) {
			return reject(run, AnalysisScopeDecision.incomplete("분석 요청을 입력해 주세요."), "INPUT_REQUIRED");
		}
		if (instruction.length() > AnalysisScopePolicy.MAX_INPUT_LENGTH) {
			return reject(
				run,
				AnalysisScopeDecision.rejected("요청이 허용된 길이를 초과했습니다."),
				"INPUT_TOO_LONG"
			);
		}

		run.start(now());
		persistBestEffort(run, null);
		var recorder = recorderFactory.create(run.id());
		try {
			var options = ProcessOptions.DEFAULT.withContextId(run.id().value());
			var outcome = AgentInvocation.builder(agentPlatform)
				.options(options)
				.build(InfluencerAnalysisOutcome.class)
				.invoke(new UserInput(instruction));
			applyRecorder(run, recorder);
			return finalizeOutcome(run, outcome);
		}
		catch (RuntimeException exception) {
			log.warn("Embabel analysis execution failed: runId={}", run.id().value());
			applyRecorder(run, recorder);
			run.fail(now(), "AGENT_EXECUTION_FAILED", "Agent execution failed");
			persistBestEffort(run, null);
			return resource(run, InfluencerAnalysisOutcome.failed("분석 실행 중 오류가 발생했습니다."));
		}
		finally {
			recorder.close();
		}
	}

	private AnalyzeInfluencerPostsResource reject(
		AnalysisRun run,
		AnalysisScopeDecision decision,
		String outcomeCode
	) {
		var outcome = InfluencerAnalysisOutcome.refused(decision);
		run.reject(now(), outcomeCode, outcome.message());
		persistBestEffort(run, null);
		return resource(run, outcome);
	}

	private AnalyzeInfluencerPostsResource finalizeOutcome(
		AnalysisRun run,
		InfluencerAnalysisOutcome outcome
	) {
		if (outcome.status() == InfluencerAnalysisOutcome.Status.COMPLETED) {
			run.complete(now(), "ANALYSIS_COMPLETED", outcome.message());
			persistBestEffort(run, outcome.report());
		}
		else {
			run.reject(now(), "OUT_OF_SCOPE", outcome.message());
			persistBestEffort(run, null);
		}
		return resource(run, outcome);
	}

	private void applyRecorder(AnalysisRun run, AnalysisRunFlightRecorder recorder) {
		if (recorder.processId() != null) {
			run.attachEmbabelProcess(recorder.processId());
		}
		run.recordMetrics(recorder.metrics());
		if (!recorder.traceAvailable()) {
			run.degradeTrace(recorder.warningCode());
		}
	}

	private void persistBestEffort(AnalysisRun run, InfluencerAnalysisReport report) {
		try {
			runStore.save(run, report);
		}
		catch (RuntimeException exception) {
			log.warn("Analysis run recording degraded: runId={}", run.id().value());
			run.degradeTrace(AnalysisRunFlightRecorder.STORAGE_WARNING);
		}
	}

	private AnalyzeInfluencerPostsResource resource(AnalysisRun run, InfluencerAnalysisOutcome outcome) {
		return AnalyzeInfluencerPostsResource.from(
			run.id(), outcome, run.traceAvailable(), run.warningCode()
		);
	}

	private Instant now() {
		return clock.instant();
	}
}
