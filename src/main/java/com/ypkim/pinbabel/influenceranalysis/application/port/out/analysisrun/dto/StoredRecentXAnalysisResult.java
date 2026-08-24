package com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public record StoredRecentXAnalysisResult(
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
	long durationMs,
	List<Company> companies,
	List<String> warnings,
	String disclaimer
) {
	public StoredRecentXAnalysisResult {
		companies = List.copyOf(companies);
		warnings = List.copyOf(warnings);
	}

	public record Company(
		String mention,
		String overallSentiment,
		int positiveCount,
		int negativeCount,
		int neutralCount,
		int uncertainCount,
		boolean conflicting,
		double confidence,
		List<Evidence> evidence
	) {
		public Company { evidence = List.copyOf(evidence); }
	}

	public record Evidence(
		String postId,
		Instant publishedAt,
		URI sourceUrl,
		String excerpt,
		String sentiment,
		String rationale,
		double confidence
	) {
	}
}
