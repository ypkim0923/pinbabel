package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import java.util.List;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record RecentXCompanyAnalysis(
	XAccountHandle account,
	int analyzedPostCount,
	List<RecentCompanySummary> companies,
	List<String> warnings,
	int xApiRequestCount,
	int llmCallCount
) {

	public RecentXCompanyAnalysis {
		companies = List.copyOf(companies);
		warnings = List.copyOf(warnings);
	}

	@Identity
	public String analysisIdentity() {
		return account.username();
	}
}
