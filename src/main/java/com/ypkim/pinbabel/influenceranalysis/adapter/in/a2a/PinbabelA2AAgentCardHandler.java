package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2a;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.QueryAnalysisCapabilitiesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.InfluencerAnalysisReportResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InternalError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.NonStreamingJSONRPCRequest;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.a2a.spec.UnsupportedOperationError;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("fixture & api")
@PrimaryAdapter
public class PinbabelA2AAgentCardHandler implements AgentCardHandler {

	private final SubmitInfluencerAnalysisUseCase submitUseCase;
	private final QueryAnalysisRunsUseCase queryUseCase;
	private final String baseUrl;
	private final Set<String> supportedPlatforms;
	private final SubmitRecentXAnalysisUseCase submitRecentXUseCase;
	private final QueryRecentXAnalysisUseCase queryRecentXUseCase;

	@Autowired
	public PinbabelA2AAgentCardHandler(
		SubmitInfluencerAnalysisUseCase submitUseCase,
		QueryAnalysisRunsUseCase queryUseCase,
		QueryAnalysisCapabilitiesUseCase capabilitiesUseCase,
		ObjectProvider<SubmitRecentXAnalysisUseCase> submitRecentXUseCase,
		ObjectProvider<QueryRecentXAnalysisUseCase> queryRecentXUseCase,
		@Value("${pinbabel.api.base-url:http://127.0.0.1:8080}") String baseUrl
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.supportedPlatforms = Set.copyOf(capabilitiesUseCase.capabilities().supportedPlatforms());
		this.submitRecentXUseCase = submitRecentXUseCase.getIfAvailable();
		this.queryRecentXUseCase = queryRecentXUseCase.getIfAvailable();
	}

	public PinbabelA2AAgentCardHandler(
		SubmitInfluencerAnalysisUseCase submitUseCase,
		QueryAnalysisRunsUseCase queryUseCase,
		QueryAnalysisCapabilitiesUseCase capabilitiesUseCase,
		String baseUrl
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.supportedPlatforms = Set.copyOf(capabilitiesUseCase.capabilities().supportedPlatforms());
		this.submitRecentXUseCase = null;
		this.queryRecentXUseCase = null;
	}

	public PinbabelA2AAgentCardHandler(
		SubmitInfluencerAnalysisUseCase submitUseCase,
		QueryAnalysisRunsUseCase queryUseCase,
		QueryAnalysisCapabilitiesUseCase capabilitiesUseCase,
		SubmitRecentXAnalysisUseCase submitRecentXUseCase,
		QueryRecentXAnalysisUseCase queryRecentXUseCase,
		String baseUrl
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.supportedPlatforms = Set.copyOf(capabilitiesUseCase.capabilities().supportedPlatforms());
		this.submitRecentXUseCase = submitRecentXUseCase;
		this.queryRecentXUseCase = queryRecentXUseCase;
	}

	@Override
	public String getPath() {
		return "a2a";
	}

	@Override
	public AgentCard agentCard(String scheme, String host, int port) {
		var skill = new AgentSkill.Builder()
			.id("analyze-stock-influencer-posts")
			.name("주식 인플루언서 SNS 종목 평가 분석")
			.description("특정 기간의 공개 SNS 포스트에서 종목별 긍정·부정·중립·판단 불가 평가를 분석합니다.")
			.tags(List.of("stocks", "social-media", "sentiment"))
			.examples(examples())
			.inputModes(List.of("text/plain"))
			.outputModes(List.of("application/json", "text/plain"))
			.build();
		var skills = new java.util.ArrayList<AgentSkill>();
		skills.add(skill);
		if (supportedPlatforms.contains("x") && submitRecentXUseCase != null) {
			skills.add(new AgentSkill.Builder()
				.id("analyze-recent-x-companies")
				.name("최근 X 회사·감정 분석")
				.description("계정의 답글·재게시를 제외한 최근 포스트 최대 10개에서 회사 표현과 감정을 분석합니다.")
				.tags(List.of("x", "stocks", "sentiment", "recent-posts"))
				.examples(List.of("DataPart: {\"operation\":\"analyzeRecentXCompanies\",\"account\":\"@aleabitoreddit\"}"))
				.inputModes(List.of("application/json"))
				.outputModes(List.of("application/json"))
				.build());
		}
		return new AgentCard.Builder()
			.name("Pinbabel")
			.description("주식 인플루언서 공개 SNS 종목 평가 분석 Agent")
			.url(baseUrl + "/a2a")
			.version("0.0.1")
			.protocolVersion("0.3.0")
			.preferredTransport("JSONRPC")
			.capabilities(new AgentCapabilities.Builder()
				.streaming(false).pushNotifications(false).stateTransitionHistory(false).build())
			.defaultInputModes(List.of("text/plain"))
			.defaultOutputModes(List.of("application/json", "text/plain"))
			.skills(List.copyOf(skills))
			.build();
	}

	private List<String> examples() {
		if (supportedPlatforms.contains("x")) {
			return List.of(
				"x의 XDevelopers를 2026-08-22T00:00:00Z부터 2026-08-24T00:00:00Z까지 UTC 기준으로 NASDAQ 분석해줘"
			);
		}
		return List.of(
			"fixture-social의 market_maven을 2025-01-01T00:00:00Z부터 2025-01-04T00:00:00Z까지 UTC 기준으로 NASDAQ 분석해줘"
		);
	}

	@Override
	public JSONRPCResponse<?> handleJsonRpc(NonStreamingJSONRPCRequest<?> request) {
		try {
			if (request instanceof SendMessageRequest send) {
				return sendMessage(send);
			}
			if (request instanceof GetTaskRequest get) {
				return getTask(get);
			}
			return new JSONRPCErrorResponse(request.getId(), new UnsupportedOperationError());
		} catch (RuntimeException exception) {
			return new JSONRPCErrorResponse(request.getId(), new InternalError("Pinbabel A2A request failed"));
		}
	}

	@Override
	public String infoString(Boolean verbose, int indent) {
		return " ".repeat(Math.max(0, indent)) + "Pinbabel A2A 0.3 handler";
	}

	private JSONRPCResponse<?> sendMessage(SendMessageRequest request) {
		var message = request.getParams() == null ? null : request.getParams().message();
		if (message == null || message.getTaskId() != null || message.getParts() == null || message.getParts().size() != 1) {
			return new SendMessageResponse(request.getId(), new InvalidParamsError(
				"A new task requires exactly one part and no taskId"
			));
		}
		if (message.getParts().getFirst() instanceof DataPart dataPart) {
			return sendRecentXMessage(request, dataPart);
		}
		if (!(message.getParts().getFirst() instanceof TextPart textPart) || textPart.getText().isBlank()) {
			return new SendMessageResponse(request.getId(), new InvalidParamsError("Text input must not be blank"));
		}
		var submission = submitUseCase.submit(new SubmitInfluencerAnalysisCommand(textPart.getText()));
		return new SendMessageResponse(request.getId(), submittedTask(submission));
	}

	private JSONRPCResponse<?> sendRecentXMessage(SendMessageRequest request, DataPart dataPart) {
		var data = dataPart.getData();
		if (submitRecentXUseCase == null || data == null
			|| !"analyzeRecentXCompanies".equals(data.get("operation"))
			|| !(data.get("account") instanceof String account) || account.isBlank() || account.length() > 16) {
			return new SendMessageResponse(request.getId(), new InvalidParamsError(
				"Recent X input requires operation=analyzeRecentXCompanies and a non-blank account"
			));
		}
		return new SendMessageResponse(request.getId(), submittedTask(submitRecentXUseCase.submit(account)));
	}

	private Task submittedTask(com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource submission) {
		return new Task.Builder()
			.id(submission.runId())
			.contextId(submission.correlationId())
			.status(new TaskStatus(toState(submission.status())))
			.metadata(metadata(submission.outcomeCode(), submission.outcomeSummary()))
			.build();
	}

	private JSONRPCResponse<?> getTask(GetTaskRequest request) {
		if (request.getParams() == null) {
			return new GetTaskResponse(request.getId(), new InvalidParamsError("Task query params are required"));
		}
		try {
			var runId = new AnalysisRunId(request.getParams().id());
			if (queryRecentXUseCase != null) {
				var recent = queryRecentXUseCase.findRecentRun(runId);
				if (recent.isPresent()) {
					return new GetTaskResponse(request.getId(), recentTask(recent.orElseThrow()));
				}
			}
			return queryUseCase.findRun(runId)
				.<JSONRPCResponse<?>>map(run -> new GetTaskResponse(request.getId(), task(run)))
				.orElseGet(() -> new GetTaskResponse(request.getId(), new TaskNotFoundError()));
		} catch (IllegalArgumentException exception) {
			return new GetTaskResponse(request.getId(), new TaskNotFoundError());
		}
	}

	private Task recentTask(RecentXAnalysisDetailResource run) {
		var builder = new Task.Builder()
			.id(run.runId())
			.contextId(run.correlationId())
			.status(new TaskStatus(toState(run.status())))
			.metadata(metadata(run.outcomeCode(), run.outcomeSummary()));
		if ("COMPLETED".equals(run.status()) || "FAILED".equals(run.status()) || "REJECTED".equals(run.status())) {
			builder.artifacts(List.of(new Artifact.Builder()
				.artifactId("recent-x-analysis-" + run.runId())
				.name("recent-x-company-analysis")
				.description("최근 X 포스트의 회사 표현, 감정, 근거와 비용 상한")
				.parts(new DataPart(recentResultData(run)))
				.build()));
		}
		return builder.build();
	}

	private Map<String, Object> recentResultData(RecentXAnalysisDetailResource run) {
		var result = run.result();
		var data = new java.util.LinkedHashMap<String, Object>();
		data.put("account", result.account());
		data.put("analyzedPostCount", result.analyzedPostCount());
		data.put("commentsExcluded", result.commentsExcluded());
		data.put("repostsExcluded", result.repostsExcluded());
		data.put("cacheHit", result.cacheHit());
		if (result.xApiRequestsThisCall() != null) data.put("xApiRequestsThisCall", result.xApiRequestsThisCall());
		if (result.llmCallsThisCall() != null) data.put("llmCallsThisCall", result.llmCallsThisCall());
		data.put("xApiRequestBudget", result.xApiRequestBudget());
		data.put("llmCallBudget", result.llmCallBudget());
		data.put("companies", result.companies().stream().map(company -> Map.<String, Object>of(
			"mention", company.mention(),
			"overallSentiment", company.overallSentiment(),
			"positiveCount", company.positiveCount(),
			"negativeCount", company.negativeCount(),
			"neutralCount", company.neutralCount(),
			"uncertainCount", company.uncertainCount(),
			"conflicting", company.conflicting(),
			"confidence", company.confidence(),
			"evidence", company.evidence().stream().map(evidence -> Map.<String, Object>of(
				"postId", evidence.postId(),
				"publishedAt", evidence.publishedAt().toString(),
				"sourceUrl", evidence.sourceUrl().toString(),
				"excerpt", evidence.excerpt(),
				"sentiment", evidence.sentiment(),
				"rationale", evidence.rationale(),
				"confidence", evidence.confidence()
			)).toList()
		)).toList());
		data.put("warnings", result.warnings());
		data.put("disclaimer", result.disclaimer());
		return Map.copyOf(data);
	}

	private Task task(AnalysisRunDetailResource run) {
		var builder = new Task.Builder()
			.id(run.runId())
			.contextId(run.correlationId())
			.status(new TaskStatus(toState(run.status())))
			.metadata(metadata(run.outcomeCode(), run.outcomeSummary()));
		if (run.report() != null) {
			builder.artifacts(List.of(new Artifact.Builder()
				.artifactId("analysis-report-" + run.runId())
				.name("influencer-analysis-report")
				.description("종목 평가, 근거와 분석 한계를 포함한 Pinbabel 보고서")
				.parts(new DataPart(reportData(run.report())))
				.build()));
		}
		return builder.build();
	}

	private Map<String, Object> reportData(InfluencerAnalysisReportResource report) {
		var result = new java.util.LinkedHashMap<String, Object>();
		result.put("platform", report.platform());
		result.put("influencerId", report.influencerId());
		result.put("period", Map.of(
			"startInclusive", report.period().startInclusive().toString(),
			"endExclusive", report.period().endExclusive().toString(),
			"timezone", report.period().timezone()
		));
		result.put("instruments", report.instrumentSummaries().stream().map(item -> Map.<String, Object>of(
			"instrumentId", item.instrumentId(), "ticker", item.ticker(),
			"displayName", item.displayName(), "sentiment", item.overallSentiment(),
			"positiveCount", item.positiveCount(), "negativeCount", item.negativeCount(),
			"neutralCount", item.neutralCount(), "uncertainCount", item.uncertainCount(),
			"conflicting", item.conflicting(), "evidencePostIds", item.evidencePostIds()
		)).toList());
		result.put("evidence", report.evidence().stream().map(this::evidenceData).toList());
		result.put("warnings", report.warnings());
		result.put("disclaimer", report.disclaimer());
		return Map.copyOf(result);
	}

	private Map<String, Object> evidenceData(InfluencerAnalysisReportResource.EvidenceResource evidence) {
		var result = new java.util.LinkedHashMap<String, Object>();
		result.put("postId", evidence.postId());
		result.put("platform", evidence.platform());
		result.put("authorId", evidence.authorId());
		result.put("publishedAt", evidence.publishedAt().toString());
		result.put("url", evidence.url());
		result.put("source", evidence.source());
		if (evidence.instrumentId() != null) result.put("instrumentId", evidence.instrumentId());
		if (evidence.ticker() != null) result.put("ticker", evidence.ticker());
		result.put("sentiment", evidence.sentiment());
		result.put("excerpt", evidence.excerpt());
		result.put("rationale", evidence.rationale());
		return Map.copyOf(result);
	}

	private Map<String, Object> metadata(String outcomeCode, String outcomeSummary) {
		var result = new java.util.LinkedHashMap<String, Object>();
		if (outcomeCode != null) result.put("outcomeCode", outcomeCode);
		if (outcomeSummary != null) result.put("outcomeSummary", outcomeSummary);
		result.put("disclaimer", "공개 SNS 발언에 대한 자동 분석이며 투자 자문이 아닙니다.");
		return Map.copyOf(result);
	}

	private TaskState toState(String status) {
		return switch (status) {
			case "CREATED" -> TaskState.SUBMITTED;
			case "RUNNING" -> TaskState.WORKING;
			case "COMPLETED" -> TaskState.COMPLETED;
			case "FAILED" -> TaskState.FAILED;
			case "REJECTED" -> TaskState.REJECTED;
			default -> TaskState.UNKNOWN;
		};
	}
}
