package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.stereotype.Component;

@Component
@PrimaryAdapter
public class PinbabelCliRenderer {

	public String render(AnalyzeInfluencerPostsResource resource) {
		var result = new StringBuilder()
			.append("runId: ").append(resource.runId()).append('\n')
			.append("status: ").append(resource.status()).append('\n')
			.append("message: ").append(resource.message()).append('\n')
			.append("traceAvailable: ").append(resource.traceAvailable()).append('\n');
		if (resource.report() != null) {
			result.append("report: ").append(resource.report()).append('\n');
		}
		for (var warning : resource.warnings()) {
			result.append("warning: ").append(warning).append('\n');
		}
		return result.append("disclaimer: ").append(resource.disclaimer()).toString();
	}

	public String renderRecentRuns(List<AnalysisRunSummaryResource> runs) {
		if (runs.isEmpty()) {
			return "실행 기록이 없습니다.";
		}
		var result = new StringBuilder("recentRuns: ").append(runs.size()).append('\n');
		for (var run : runs) {
			result.append("- runId: ").append(run.runId())
				.append(", status: ").append(run.status())
				.append(", createdAt: ").append(run.createdAt())
				.append(", durationMs: ").append(run.durationMs())
				.append(", traceAvailable: ").append(run.traceAvailable())
				.append('\n');
		}
		return result.toString().stripTrailing();
	}

	public String renderRunDetail(AnalysisRunDetailResource run) {
		var result = new StringBuilder()
			.append("runId: ").append(run.runId()).append('\n')
			.append("status: ").append(run.status()).append('\n')
			.append("createdAt: ").append(run.createdAt()).append('\n')
			.append("startedAt: ").append(run.startedAt()).append('\n')
			.append("completedAt: ").append(run.completedAt()).append('\n')
			.append("durationMs: ").append(run.durationMs()).append('\n')
			.append("traceAvailable: ").append(run.traceAvailable()).append('\n');
		appendIfPresent(result, "warning", run.warningCode());
		appendIfPresent(result, "outcomeCode", run.outcomeCode());
		appendIfPresent(result, "outcomeSummary", run.outcomeSummary());
		appendIfPresent(result, "promptTokens", run.metrics().promptTokens());
		appendIfPresent(result, "completionTokens", run.metrics().completionTokens());
		appendIfPresent(result, "costUsd", run.metrics().costUsd());
		if (!run.metrics().models().isEmpty()) {
			result.append("models: ").append(String.join(", ", run.metrics().models())).append('\n');
		}
		if (run.report() != null) {
			result.append("report: ").append(run.report()).append('\n');
		}
		result.append("events: ").append(run.events().size()).append('\n');
		for (var event : run.events()) {
			result.append("- #").append(event.sequence())
				.append(' ').append(event.occurredAt())
				.append(' ').append(event.eventType());
			if (event.actionName() != null) {
				result.append(" action=").append(event.actionName());
			}
			if (event.toolName() != null) {
				result.append(" tool=").append(event.toolName());
			}
			if (event.modelName() != null) {
				result.append(" model=").append(event.modelName());
			}
			if (event.durationMs() != null) {
				result.append(" durationMs=").append(event.durationMs());
			}
			if (event.successful() != null) {
				result.append(" successful=").append(event.successful());
			}
			result.append('\n');
		}
		return result.toString().stripTrailing();
	}

	private void appendIfPresent(StringBuilder result, String name, Object value) {
		if (value != null) {
			result.append(name).append(": ").append(value).append('\n');
		}
	}
}
