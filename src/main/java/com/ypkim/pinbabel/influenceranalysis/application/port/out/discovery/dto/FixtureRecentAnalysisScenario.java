package com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import java.util.Map;

public record FixtureRecentAnalysisScenario(
	InfluencerProfileId profileId,
	XAccountHandle account,
	CollectedPosts posts,
	RecentCompanyMentionAssessments assessments,
	Map<String, String> analyzedTextByPostId
) {
	public FixtureRecentAnalysisScenario {
		analyzedTextByPostId = Map.copyOf(analyzedTextByPostId);
	}
}
