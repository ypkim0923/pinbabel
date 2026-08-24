package com.ypkim.pinbabel.influenceranalysis.application.service;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisScopeDecision;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisCorrelationId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisScopePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisExecutionLauncher;
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
@Profile("fixture")
public class EmbabelInfluencerAnalysisService implements AnalyzeInfluencerPostsUseCase, SubmitInfluencerAnalysisUseCase {
	private static final Logger log = LoggerFactory.getLogger(EmbabelInfluencerAnalysisService.class);

	private final AgentPlatform agentPlatform;
	private final AnalysisRunStore runStore;
	private final AnalysisRunFlightRecorderFactory recorderFactory;
	private final AnalysisExecutionLauncher executionLauncher;
	private final Clock clock;

	@Autowired
	public EmbabelInfluencerAnalysisService(
		AgentPlatform agentPlatform,
		AnalysisRunStore runStore,
		AnalysisRunFlightRecorderFactory recorderFactory,
		AnalysisExecutionLauncher executionLauncher
	) {
		this(agentPlatform, runStore, recorderFactory, executionLauncher, Clock.systemUTC());
	}

	EmbabelInfluencerAnalysisService(
		AgentPlatform agentPlatform,
		AnalysisRunStore runStore,
		AnalysisRunFlightRecorderFactory recorderFactory,
		Clock clock
	) {
		this(agentPlatform, runStore, recorderFactory, (runId, execution) -> false, clock);
	}

	EmbabelInfluencerAnalysisService(
		AgentPlatform agentPlatform,
		AnalysisRunStore runStore,
		AnalysisRunFlightRecorderFactory recorderFactory,
		AnalysisExecutionLauncher executionLauncher,
		Clock clock
	) {
		this.agentPlatform = agentPlatform;
		this.runStore = runStore;
		this.recorderFactory = recorderFactory;
		this.executionLauncher = executionLauncher;
		this.clock = clock;
	}

	@Override
	public AnalyzeInfluencerPostsResource analyze(AnalyzeInfluencerPostsCommand command) {
		var run = newRun();
		persistBestEffort(run, null);
		var instruction = command == null ? null : command.instruction();
		var rejection = validate(instruction);
		if (rejection != null) {
			return reject(run, rejection.decision(), rejection.outcomeCode());
		}
		return execute(run, instruction);
	}

	@Override
	public AnalysisSubmissionResource submit(SubmitInfluencerAnalysisCommand command) {
		var run = newRun();
		runStore.save(run, null);
		var instruction = command == null ? null : command.instruction();
		var rejection = validate(instruction);
		if (rejection != null) {
			rejectRequired(run, rejection.decision(), rejection.outcomeCode());
			return AnalysisSubmissionResource.from(run);
		}

		var accepted = AnalysisSubmissionResource.from(run);
		if (!executionLauncher.launch(run.id(), () -> execute(run, instruction))) {
			run.reject(now(), "EXECUTION_CAPACITY_EXCEEDED", "Analysis execution capacity is exhausted");
			runStore.save(run, null);
			return AnalysisSubmissionResource.from(run);
		}
		return accepted;
	}

	private AnalyzeInfluencerPostsResource execute(AnalysisRun run, String instruction) {
		run.start(now());
		persistBestEffort(run, null);
		var recorder = createRecorder(run);
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
			closeRecorder(run, recorder);
		}
	}

	private AnalysisRunFlightRecorder createRecorder(AnalysisRun run) {
		try {
			return recorderFactory.create(run.id());
		}
		catch (RuntimeException exception) {
			log.warn("Analysis trace recorder creation failed: runId={}", run.id().value());
			run.degradeTrace(AnalysisRunFlightRecorder.STORAGE_WARNING);
			persistBestEffort(run, null);
			return null;
		}
	}

	private void closeRecorder(AnalysisRun run, AnalysisRunFlightRecorder recorder) {
		if (recorder == null) {
			return;
		}
		try {
			recorder.close();
		}
		catch (RuntimeException exception) {
			log.warn("Analysis trace recorder close failed: runId={}", run.id().value());
		}
	}

	private AnalysisRun newRun() {
		return AnalysisRun.create(AnalysisRunId.newId(), AnalysisCorrelationId.newId(), now());
	}

	private Rejection validate(String instruction) {
		if (instruction == null || instruction.isBlank()) {
			return new Rejection(AnalysisScopeDecision.incomplete("분석 요청을 입력해 주세요."), "INPUT_REQUIRED");
		}
		if (instruction.length() > AnalysisScopePolicy.MAX_INPUT_LENGTH) {
			return new Rejection(
				AnalysisScopeDecision.rejected("요청이 허용된 길이를 초과했습니다."),
				"INPUT_TOO_LONG"
			);
		}
		return null;
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

	private void rejectRequired(AnalysisRun run, AnalysisScopeDecision decision, String outcomeCode) {
		var outcome = InfluencerAnalysisOutcome.refused(decision);
		run.reject(now(), outcomeCode, outcome.message());
		runStore.save(run, null);
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
		if (recorder == null) {
			return;
		}
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
			run.id(), run.correlationId(), outcome, run.traceAvailable(), run.warningCode()
		);
	}

	private Instant now() {
		return clock.instant();
	}

	private record Rejection(AnalysisScopeDecision decision, String outcomeCode) {
	}
}
