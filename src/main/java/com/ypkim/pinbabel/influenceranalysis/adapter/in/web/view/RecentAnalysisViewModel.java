package com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record RecentAnalysisViewModel(
	String status,
	String statusLabel,
	String message,
	boolean terminal,
	boolean automaticPolling,
	boolean pollingBudgetExceeded,
	String runId,
	String runReference,
	String createdAt,
	List<SentimentSectionViewModel> sections,
	ExperimentViewModel experiment,
	String recoveryGuidance,
	boolean retryAllowed
) {
	public RecentAnalysisViewModel {
		sections = List.copyOf(sections);
	}

	public record SentimentSectionViewModel(
		String code,
		String label,
		String emptyMessage,
		List<CompanyViewModel> companies
	) {
		public SentimentSectionViewModel {
			companies = List.copyOf(companies);
		}
	}

	public record CompanyViewModel(
		String name,
		int mentionCount,
		int confidencePercent,
		boolean conflicting,
		List<EvidenceViewModel> evidence
	) {
		public CompanyViewModel {
			evidence = List.copyOf(evidence);
		}
	}

	public record EvidenceViewModel(
		String postId,
		String publishedAt,
		String excerpt,
		String rationale,
		String sentiment,
		int confidencePercent,
		boolean externalLink,
		String sourceUrl,
		String sourceLabel
	) {
	}

	public record ExperimentViewModel(
		int analyzedPostCount,
		String xApiCalls,
		String llmCalls,
		int xApiBudget,
		int llmBudget,
		long durationMs,
		boolean cacheHit,
		boolean fixture,
		List<String> warnings
	) {
		public ExperimentViewModel {
			warnings = List.copyOf(warnings);
		}
	}
}
