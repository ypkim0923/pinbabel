package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FixtureRecentAnalysisSourceAdapterTest {

	private final FixtureRecentAnalysisSourceAdapter source = new FixtureRecentAnalysisSourceAdapter(
		JsonMapper.builder().build()
	);

	@Test
	void returnsTenGroundedOriginalPostsForEachFixtureProfile() {
		var scenario = source.findByProfileId(new InfluencerProfileId("growth-lab")).orElseThrow();

		assertThat(scenario.posts().posts()).hasSize(10)
			.allMatch(post -> post.kind() == PostKind.ORIGINAL)
			.allMatch(post -> "fixture".equals(post.platform()))
			.allMatch(post -> "urn".equals(post.url().getScheme()));
		assertThat(scenario.assessments().assessments()).hasSize(10)
			.allMatch(assessment -> scenario.analyzedTextByPostId().get(assessment.postId())
				.contains(assessment.mention()));
		assertThat(scenario.posts().warnings()).containsExactly("FIXTURE_DATA_NOT_LIVE");
	}

	@Test
	void doesNotExposeAScenarioForTheLiveProfile() {
		assertThat(source.findByProfileId(new InfluencerProfileId("serenity"))).isEmpty();
	}
}
