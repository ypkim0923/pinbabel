package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

import java.util.List;

public record RecentCompanyResource(
	String mention,
	String overallSentiment,
	int positiveCount,
	int negativeCount,
	int neutralCount,
	int uncertainCount,
	boolean conflicting,
	double confidence,
	List<RecentCompanyEvidenceResource> evidence
) {

	public RecentCompanyResource {
		evidence = List.copyOf(evidence);
	}

	public List<String> evidencePostIds() {
		return evidence.stream()
			.map(RecentCompanyEvidenceResource::postId)
			.distinct()
			.toList();
	}
}
