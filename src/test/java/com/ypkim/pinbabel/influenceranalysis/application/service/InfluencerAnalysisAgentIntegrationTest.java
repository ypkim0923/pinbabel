package com.ypkim.pinbabel.influenceranalysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.agent.api.event.ActionExecutionResultEvent;
import com.embabel.agent.api.event.ActionExecutionStartEvent;
import com.embabel.agent.api.event.AgentProcessPlanFormulatedEvent;
import com.embabel.agent.api.event.GoalAchievedEvent;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.ActionInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.test.common.EventSavingAgenticEventListener;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import com.embabel.agent.test.integration.IntegrationTestUtils;
import com.embabel.agent.test.integration.ScriptedLlmOperations;
import com.embabel.agent.domain.io.UserInput;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisIntent;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.adapter.out.embabel.trace.EmbabelFlightRecorderFactory;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("fixture")
class InfluencerAnalysisAgentIntegrationTest extends EmbabelMockitoIntegrationTest {

	@Test
	void fixtureProfileRegistersTypedAgentActionsConditionsAndGoals() {
		var agent = influencerAnalysisAgent();

		assertThat(agent.getActions()).hasSize(9);
		assertThat(agent.getConditions())
			.extracting(condition -> condition.getName())
			.contains("hasPosts", "noPosts", "analysisAccepted", "analysisRejected", "analysisIncomplete");
		assertThat(agent.getGoals()).hasSize(4);
	}

	@Test
	void plannerUsesToolsAndReachesReportGoalForPostsInPeriod() {
		var execution = runNormalAnalysis();

		assertThat(execution.report().instrumentSummaries()).singleElement().satisfies(summary -> {
			assertThat(summary.instrumentId()).isEqualTo("NASDAQ:NVDA");
			assertThat(summary.overallSentiment()).isEqualTo(Sentiment.POSITIVE);
			assertThat(summary.evidencePostIds()).containsExactly("post-start");
		});
		assertThat(execution.report().evidence()).singleElement().satisfies(evidence -> {
			assertThat(evidence.postId()).isEqualTo("post-start");
			assertThat(evidence.url()).isEqualTo("https://social.example/posts/post-start");
		});
		assertThat(actionNames(execution.process()))
			.containsExactly("collectPosts", "assessPosts", "buildReport");
		assertThat(execution.scriptedLlm().getToolCallsMade())
			.extracting(ScriptedLlmOperations.ToolCallRecord::getToolName)
			.containsExactly("list_posts", "read_post", "search_instruments", "read_instrument");
		assertThat(execution.scriptedLlm().getPromptsReceived()).singleElement().satisfies(prompt ->
			assertThat(prompt)
				.contains("untrusted SNS data")
				.contains("post-start")
		);
		assertLifecycleEvents(execution.listener(), 3);
	}

	@Test
	void plannerReplansToEmptyGoalWithoutLlmOrToolCalls() {
		var scriptedLlm = new ScriptedLlmOperations();
		var listener = new EventSavingAgenticEventListener();
		var process = run(emptyPeriodRequest(), scriptedLlm, listener);
		var report = process.resultOfType(InfluencerAnalysisOutcome.class).report();

		assertThat(report.instrumentSummaries()).isEmpty();
		assertThat(report.evidence()).isEmpty();
		assertThat(report.warnings()).containsExactly("NO_POSTS");
		assertThat(actionNames(process)).containsExactly("collectPosts", "buildEmptyReport");
		assertThat(scriptedLlm.getPromptsReceived()).isEmpty();
		assertThat(scriptedLlm.getToolCallsMade()).isEmpty();
		assertThat(listener.getProcessEvents().stream()
			.filter(AgentProcessPlanFormulatedEvent.class::isInstance))
			.hasSizeGreaterThanOrEqualTo(2);
		assertLifecycleEvents(listener, 2);
	}

	@Test
	void sameFixtureAndScriptProduceSameReportAndActionSequence() {
		var first = runNormalAnalysis();
		var second = runNormalAnalysis();

		assertThat(second.report()).isEqualTo(first.report());
		assertThat(actionNames(second.process())).isEqualTo(actionNames(first.process()));
	}

	@Test
	void naturalLanguageInputReachesTypedFixtureAnalysis() {
		var intent = new AnalysisIntent(
			AnalysisIntent.TaskType.ANALYZE_INFLUENCER_POSTS,
			"fixture-social",
			"0007-market-voice",
			"2026-01-01T00:00:00Z",
			"2026-01-03T00:00:00Z",
			"UTC",
			Set.of("NASDAQ")
		);
		var assessments = new PostAssessments(List.of(
			new PostAssessment("post-start", "NASDAQ:NVDA", "NVDA", Sentiment.POSITIVE, "Positive view")
		));
		var scriptedLlm = new ScriptedLlmOperations()
			.returnObject(intent)
			.callTool("list_posts", "{}")
			.callTool("read_post", "{\"postId\":\"post-start\"}")
			.callTool("search_instruments", "{\"query\":\"NVDA\"}")
			.callTool("read_instrument", "{\"instrumentId\":\"NASDAQ:NVDA\"}")
			.returnObject(assessments);
		var listener = new EventSavingAgenticEventListener();

		var process = run(
			new UserInput("fixture-social의 0007-market-voice 2026-01-01부터 2026-01-03까지 분석"),
			scriptedLlm,
			listener
		);

		var outcome = process.resultOfType(InfluencerAnalysisOutcome.class);
		assertThat(outcome.status()).isEqualTo(InfluencerAnalysisOutcome.Status.COMPLETED);
		assertThat(outcome.report().instrumentSummaries()).hasSize(1);
		assertThat(actionNames(process)).containsExactly(
			"interpretInput",
			"evaluateScope",
			"createAnalysisRequest",
			"collectPosts",
			"assessPosts",
			"buildReport"
		);
	}

	@Test
	void unrelatedNaturalLanguageReachesRefusalWithoutTools() {
		var scriptedLlm = new ScriptedLlmOperations().returnObject(
			new AnalysisIntent(AnalysisIntent.TaskType.OTHER, null, null, null, null, null, Set.of())
		);
		var listener = new EventSavingAgenticEventListener();

		var process = run(new UserInput("오늘 날씨를 알려줘"), scriptedLlm, listener);

		var outcome = process.resultOfType(InfluencerAnalysisOutcome.class);
		assertThat(outcome.status()).isEqualTo(InfluencerAnalysisOutcome.Status.REFUSED);
		assertThat(outcome.report()).isNull();
		assertThat(actionNames(process)).containsExactly("interpretInput", "evaluateScope", "refuseRequest");
		assertThat(scriptedLlm.getToolCallsMade()).isEmpty();
	}

	@Test
	void applicationServiceCorrelatesSafeEmbabelTraceWithRunId() {
		var intent = new AnalysisIntent(
			AnalysisIntent.TaskType.ANALYZE_INFLUENCER_POSTS,
			"fixture-social",
			"0007-market-voice",
			"2026-01-01T00:00:00Z",
			"2026-01-03T00:00:00Z",
			"UTC",
			Set.of("NASDAQ")
		);
		var assessments = new PostAssessments(List.of(
			new PostAssessment("post-start", "NASDAQ:NVDA", "NVDA", Sentiment.POSITIVE, "Positive view")
		));
		var scriptedLlm = new ScriptedLlmOperations()
			.returnObject(intent)
			.callTool("list_posts", "{}")
			.callTool("read_post", "{\"postId\":\"post-start\"}")
			.callTool("search_instruments", "{\"query\":\"NVDA\"}")
			.callTool("read_instrument", "{\"instrumentId\":\"NASDAQ:NVDA\"}")
			.returnObject(assessments);
		var store = new RecordingRunStore();
		var recorderFactory = new EmbabelFlightRecorderFactory(store, 100);
		AgentPlatform platform = IntegrationTestUtils
			.dummyAgentPlatform(
				scriptedLlm,
				recorderFactory,
				null,
				agentPlatform.getPlatformServices().getLogicalExpressionParser()
			)
			.deploy(influencerAnalysisAgent());
		var service = new EmbabelInfluencerAnalysisService(
			platform,
			store,
			recorderFactory,
			Clock.systemUTC()
		);

		var resource = service.analyze(new AnalyzeInfluencerPostsCommand(
			"fixture-social의 0007-market-voice 2026-01-01부터 2026-01-03까지 분석"
		));

		assertThat(resource.status()).isEqualTo("COMPLETED");
		assertThat(resource.runId()).isEqualTo(store.runId.value());
		assertThat(store.status).isEqualTo(AnalysisRunStatus.COMPLETED);
		assertThat(store.report).isNotNull();
		assertThat(store.events).extracting(AnalysisTraceEvent::eventType)
			.contains("PLAN_FORMULATED", "ACTION_STARTED", "ACTION_COMPLETED", "PROCESS_COMPLETED");
		assertThat(store.events).allSatisfy(event -> {
			assertThat(event.eventType()).doesNotContain("post-start");
			assertThat(event.eventType()).doesNotContain("Positive view");
		});
	}

	private AnalysisExecution runNormalAnalysis() {
		var assessments = new PostAssessments(List.of(
			new PostAssessment(
				"post-start",
				"NASDAQ:NVDA",
				"NVDA",
				Sentiment.POSITIVE,
				"The influencer explicitly remains positive on NVIDIA"
			)
		));
		var scriptedLlm = new ScriptedLlmOperations()
			.callTool("list_posts", "{}")
			.callTool("read_post", "{\"postId\":\"post-start\"}")
			.callTool("search_instruments", "{\"query\":\"NVDA\"}")
			.callTool("read_instrument", "{\"instrumentId\":\"NASDAQ:NVDA\"}")
			.returnObject(assessments);
		var listener = new EventSavingAgenticEventListener();
		var process = run(normalRequest(), scriptedLlm, listener);
		return new AnalysisExecution(
			process,
			process.resultOfType(InfluencerAnalysisOutcome.class).report(),
			scriptedLlm,
			listener
		);
	}

	private AgentProcess run(
		Object input,
		ScriptedLlmOperations scriptedLlm,
		EventSavingAgenticEventListener listener
	) {
		AgentPlatform platform = IntegrationTestUtils
			.dummyAgentPlatform(
				scriptedLlm,
				listener,
				null,
				agentPlatform.getPlatformServices().getLogicalExpressionParser()
			)
			.deploy(influencerAnalysisAgent());
		var options = ProcessOptions.DEFAULT.withListener(listener);
		return AgentInvocation.builder(platform)
			.options(options)
			.build(InfluencerAnalysisOutcome.class)
			.run(input);
	}

	private Agent influencerAnalysisAgent() {
		return agentPlatform.agents().stream()
			.filter(agent -> agent.getDescription().contains("stock influencer"))
			.findFirst()
			.orElseThrow();
	}

	private List<String> actionNames(AgentProcess process) {
		return process.getHistory().stream()
			.map(ActionInvocation::getActionName)
			.map(this::simpleActionName)
			.toList();
	}

	private String simpleActionName(String actionName) {
		var separator = actionName.lastIndexOf('.');
		return separator < 0 ? actionName : actionName.substring(separator + 1);
	}

	private void assertLifecycleEvents(EventSavingAgenticEventListener listener, int actionCount) {
		assertThat(listener.getProcessEvents().stream()
			.filter(ActionExecutionStartEvent.class::isInstance)).hasSize(actionCount);
		assertThat(listener.getProcessEvents().stream()
			.filter(ActionExecutionResultEvent.class::isInstance)).hasSize(actionCount);
		assertThat(listener.getProcessEvents().stream()
			.filter(GoalAchievedEvent.class::isInstance)).hasSize(1);
	}

	private InfluencerAnalysisRequest normalRequest() {
		return request(
			Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-01-03T00:00:00Z")
		);
	}

	private InfluencerAnalysisRequest emptyPeriodRequest() {
		return request(
			Instant.parse("2027-01-01T00:00:00Z"),
			Instant.parse("2027-01-02T00:00:00Z")
		);
	}

	private InfluencerAnalysisRequest request(Instant startInclusive, Instant endExclusive) {
		return new InfluencerAnalysisRequest(
			"fixture-social",
			"0007-market-voice",
			new AnalysisPeriod(startInclusive, endExclusive, ZoneOffset.UTC),
			Set.of("NASDAQ")
		);
	}

	private record AnalysisExecution(
		AgentProcess process,
		InfluencerAnalysisReport report,
		ScriptedLlmOperations scriptedLlm,
		EventSavingAgenticEventListener listener
	) {
	}

	private static final class RecordingRunStore implements AnalysisRunStore {

		private final List<AnalysisTraceEvent> events = new ArrayList<>();
		private AnalysisRunId runId;
		private AnalysisRunStatus status;
		private InfluencerAnalysisReport report;

		@Override
		public void save(AnalysisRun run, InfluencerAnalysisReport report) {
			this.runId = run.id();
			this.status = run.status();
			if (report != null) {
				this.report = report;
			}
		}

		@Override
		public void append(AnalysisRunId runId, AnalysisTraceEvent event) {
			events.add(event);
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
}
