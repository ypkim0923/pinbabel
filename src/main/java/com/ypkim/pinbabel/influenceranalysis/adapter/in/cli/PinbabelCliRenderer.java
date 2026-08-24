package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.stereotype.Component;

@Component
@PrimaryAdapter
public class PinbabelCliRenderer {

	public String renderRecentCompanies(RecentMentionedCompaniesResource resource) {
		var result = recentAnalysisHeader(
			resource.status(), resource.message(), resource.account(), resource.analyzedPostCount(),
			resource.commentsExcluded(), resource.repostsExcluded(), resource.cacheHit(),
			resource.xApiRequestsThisCall(), resource.llmCallsThisCall(),
			resource.xApiRequestBudget(), resource.llmCallBudget(), resource.durationMs()
		);
		appendCompanies(result, "companies", resource.companies());
		resource.warnings().forEach(warning -> result.append("warning: ").append(warning).append('\n'));
		return result.append("disclaimer: ").append(resource.disclaimer()).toString().stripTrailing();
	}

	public String renderRecentCompanySentiment(RecentCompanySentimentResource resource) {
		var result = recentAnalysisHeader(
			resource.status(), resource.message(), resource.account(), resource.analyzedPostCount(),
			resource.commentsExcluded(), resource.repostsExcluded(), resource.cacheHit(),
			resource.xApiRequestsThisCall(), resource.llmCallsThisCall(),
			resource.xApiRequestBudget(), resource.llmCallBudget(), resource.durationMs()
		);
		appendCompanies(result, "positiveCompanies", resource.positiveCompanies());
		appendCompanies(result, "negativeCompanies", resource.negativeCompanies());
		resource.warnings().forEach(warning -> result.append("warning: ").append(warning).append('\n'));
		return result.append("disclaimer: ").append(resource.disclaimer()).toString().stripTrailing();
	}

	public String renderRecommendations(XStockInfluencerRecommendationsResource resource) {
		var result = new StringBuilder()
			.append("message: ").append(resource.message()).append('\n')
			.append("xApiUsed: ").append(resource.xApiUsed()).append('\n')
			.append("llmUsed: ").append(resource.llmUsed()).append('\n')
			.append("accounts: ").append(resource.accounts().size()).append('\n');
		for (var account : resource.accounts()) {
			result.append("- ").append(account.handle())
				.append(" (").append(account.displayName()).append(")")
				.append(", platform: ").append(account.platform())
				.append(", selectionBasis: ").append(account.selectionBasis())
				.append(", reason: ").append(account.reason())
				.append('\n');
		}
		return result.toString().stripTrailing();
	}

	public String render(AnalyzeInfluencerPostsResource resource) {
		var result = new StringBuilder()
			.append("runId: ").append(resource.runId()).append('\n')
			.append("correlationId: ").append(resource.correlationId()).append('\n')
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
				.append(", correlationId: ").append(run.correlationId())
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
			.append("correlationId: ").append(run.correlationId()).append('\n')
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

	public String renderEvaluation(EvaluationRunDetailResource run) {
		var result = new StringBuilder()
			.append("evaluationRunId: ").append(run.evaluationRunId()).append('\n')
			.append("dataset: ").append(run.datasetId()).append(" v").append(run.datasetVersion()).append('\n')
			.append("cases: ").append(run.completedCaseCount()).append('/').append(run.caseCount()).append('\n')
			.append("exactMatches: ").append(run.exactMatchCaseCount()).append('\n')
			.append("instrumentPrecision: ").append(formatScore(run.instrumentPrecision())).append('\n')
			.append("instrumentRecall: ").append(formatScore(run.instrumentRecall())).append('\n')
			.append("instrumentF1: ").append(formatScore(run.instrumentF1())).append('\n')
			.append("sentimentAccuracy: ").append(formatScore(run.sentimentAccuracy())).append('\n')
			.append("evidenceRecall: ").append(formatScore(run.evidenceRecall())).append('\n');
		for (var evaluationCase : run.cases()) {
			result.append("- caseId: ").append(evaluationCase.caseId())
				.append(", analysisRunId: ").append(evaluationCase.analysisRunId())
				.append(", status: ").append(evaluationCase.analysisStatus())
				.append(", exactMatch: ").append(evaluationCase.exactMatch())
				.append(", instrumentF1: ").append(formatScore(evaluationCase.instrumentF1()))
				.append(", sentimentAccuracy: ").append(formatScore(evaluationCase.sentimentAccuracy()))
				.append(", evidenceRecall: ").append(formatScore(evaluationCase.evidenceRecall()));
			if (!evaluationCase.mismatches().isEmpty()) {
				result.append(", mismatches: ").append(String.join(", ", evaluationCase.mismatches()));
			}
			result.append('\n');
		}
		return result.toString().stripTrailing();
	}

	public String renderRecentEvaluations(List<EvaluationRunSummaryResource> runs) {
		if (runs.isEmpty()) {
			return "평가 실행 기록이 없습니다.";
		}
		var result = new StringBuilder("recentEvaluations: ").append(runs.size()).append('\n');
		for (var run : runs) {
			result.append("- evaluationRunId: ").append(run.evaluationRunId())
				.append(", dataset: ").append(run.datasetId()).append(" v").append(run.datasetVersion())
				.append(", createdAt: ").append(run.createdAt())
				.append(", instrumentF1: ").append(formatScore(run.instrumentF1()))
				.append(", sentimentAccuracy: ").append(formatScore(run.sentimentAccuracy()))
				.append('\n');
		}
		return result.toString().stripTrailing();
	}

	private void appendIfPresent(StringBuilder result, String name, Object value) {
		if (value != null) {
			result.append(name).append(": ").append(value).append('\n');
		}
	}

	private StringBuilder recentAnalysisHeader(
		String status,
		String message,
		String account,
		int analyzedPostCount,
		boolean commentsExcluded,
		boolean repostsExcluded,
		boolean cacheHit,
		Integer xApiRequestsThisCall,
		Integer llmCallsThisCall,
		int xApiRequestBudget,
		int llmCallBudget,
		long durationMs
	) {
		return new StringBuilder()
			.append("status: ").append(status).append('\n')
			.append("message: ").append(message).append('\n')
			.append("account: ").append(account).append('\n')
			.append("analyzedPostCount: ").append(analyzedPostCount).append('\n')
			.append("commentsExcluded: ").append(commentsExcluded).append('\n')
			.append("repostsExcluded: ").append(repostsExcluded).append('\n')
			.append("cacheHit: ").append(cacheHit).append('\n')
			.append("xApiRequestsThisCall: ").append(callCount(xApiRequestsThisCall)).append('\n')
			.append("llmCallsThisCall: ").append(callCount(llmCallsThisCall)).append('\n')
			.append("xApiRequestBudget: ").append(xApiRequestBudget).append('\n')
			.append("llmCallBudget: ").append(llmCallBudget).append('\n')
			.append("durationMs: ").append(durationMs).append('\n');
	}

	private void appendCompanies(StringBuilder result, String label, List<RecentCompanyResource> companies) {
		result.append(label).append(": ").append(companies.size()).append('\n');
		for (var company : companies) {
			result.append("- ").append(company.mention())
				.append(", sentiment: ").append(company.overallSentiment())
				.append(", positive: ").append(company.positiveCount())
				.append(", negative: ").append(company.negativeCount())
				.append(", neutral: ").append(company.neutralCount())
				.append(", uncertain: ").append(company.uncertainCount())
				.append(", conflicting: ").append(company.conflicting())
				.append(", confidence: ").append(formatScore(company.confidence()))
				.append(", evidencePostIds: ").append(company.evidencePostIds())
				.append('\n');
			for (var evidence : company.evidence()) {
				result.append("  - postId: ").append(evidence.postId())
					.append(", publishedAt: ").append(evidence.publishedAt())
					.append(", sourceUrl: ").append(evidence.sourceUrl())
					.append(", sentiment: ").append(evidence.sentiment())
					.append(", confidence: ").append(formatScore(evidence.confidence()))
					.append(", rationale: ").append(evidence.rationale())
					.append(", excerpt: ").append(evidence.excerpt())
					.append('\n');
			}
		}
	}

	private String callCount(Integer count) {
		return count == null ? "unknown" : count.toString();
	}

	private String formatScore(double score) {
		return "%.4f".formatted(score);
	}
}
