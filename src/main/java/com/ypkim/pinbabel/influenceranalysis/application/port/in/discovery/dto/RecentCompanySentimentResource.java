package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

import java.util.List;

public record RecentCompanySentimentResource(
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
	List<RecentCompanyResource> positiveCompanies,
	List<RecentCompanyResource> negativeCompanies,
	List<String> warnings,
	String disclaimer
) {

	public RecentCompanySentimentResource {
		positiveCompanies = List.copyOf(positiveCompanies);
		negativeCompanies = List.copyOf(negativeCompanies);
		warnings = List.copyOf(warnings);
	}
}
