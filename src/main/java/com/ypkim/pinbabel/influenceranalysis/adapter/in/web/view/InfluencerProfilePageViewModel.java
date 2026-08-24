package com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record InfluencerProfilePageViewModel(
	InfluencerProfileViewModel profile,
	RecentAnalysisViewModel analysis,
	boolean liveAnalysisEnabled,
	String liveSetupGuidance,
	String executionToken,
	String disclaimer
) {
}
