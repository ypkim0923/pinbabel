package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

import java.util.List;

public record RecentMentionedCompaniesResource(
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
	List<RecentCompanyResource> companies,
	List<String> warnings,
	String disclaimer
) {

	public RecentMentionedCompaniesResource {
		companies = List.copyOf(companies);
		warnings = List.copyOf(warnings);
	}
}
