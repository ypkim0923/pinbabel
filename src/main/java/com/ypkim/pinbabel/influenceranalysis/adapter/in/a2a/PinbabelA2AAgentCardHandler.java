package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2a;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
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
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("fixture & api")
@PrimaryAdapter
public class PinbabelA2AAgentCardHandler implements AgentCardHandler {

	private final SubmitInfluencerAnalysisUseCase submitUseCase;
	private final QueryAnalysisRunsUseCase queryUseCase;
	private final String baseUrl;

	public PinbabelA2AAgentCardHandler(
		SubmitInfluencerAnalysisUseCase submitUseCase,
		QueryAnalysisRunsUseCase queryUseCase,
		@Value("${pinbabel.api.base-url:http://127.0.0.1:8080}") String baseUrl
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
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
			.examples(List.of("fixture-social의 market_maven을 2025-01-01부터 2025-01-04까지 UTC 기준으로 NASDAQ 분석해줘"))
			.inputModes(List.of("text/plain"))
			.outputModes(List.of("application/json", "text/plain"))
			.build();
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
			.skills(List.of(skill))
			.build();
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
		if (message == null || message.getTaskId() != null || message.getParts() == null || message.getParts().size() != 1
			|| !(message.getParts().getFirst() instanceof TextPart textPart) || textPart.getText().isBlank()) {
			return new SendMessageResponse(request.getId(), new InvalidParamsError(
				"A new task requires exactly one non-blank text part and no taskId"
			));
		}
		var submission = submitUseCase.submit(new SubmitInfluencerAnalysisCommand(textPart.getText()));
		var task = new Task.Builder()
			.id(submission.runId())
			.contextId(submission.correlationId())
			.status(new TaskStatus(toState(submission.status())))
			.metadata(metadata(submission.outcomeCode(), submission.outcomeSummary()))
			.build();
		return new SendMessageResponse(request.getId(), task);
	}

	private JSONRPCResponse<?> getTask(GetTaskRequest request) {
		if (request.getParams() == null) {
			return new GetTaskResponse(request.getId(), new InvalidParamsError("Task query params are required"));
		}
		try {
			var runId = new AnalysisRunId(request.getParams().id());
			return queryUseCase.findRun(runId)
				.<JSONRPCResponse<?>>map(run -> new GetTaskResponse(request.getId(), task(run)))
				.orElseGet(() -> new GetTaskResponse(request.getId(), new TaskNotFoundError()));
		} catch (IllegalArgumentException exception) {
			return new GetTaskResponse(request.getId(), new TaskNotFoundError());
		}
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
		result.put("evidence", report.evidence().stream().map(item -> Map.<String, Object>ofEntries(
			Map.entry("postId", item.postId()), Map.entry("platform", item.platform()),
			Map.entry("authorId", item.authorId()), Map.entry("publishedAt", item.publishedAt().toString()),
			Map.entry("url", item.url()), Map.entry("source", item.source()),
			Map.entry("instrumentId", item.instrumentId()), Map.entry("ticker", item.ticker()),
			Map.entry("sentiment", item.sentiment()), Map.entry("excerpt", item.excerpt()),
			Map.entry("rationale", item.rationale())
		)).toList());
		result.put("warnings", report.warnings());
		result.put("disclaimer", report.disclaimer());
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
