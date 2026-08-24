package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture.FixtureRecentAnalysisSourceAdapter;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FixtureStockInfluencerAnalysisServiceTest {

	private final FixtureStockInfluencerAnalysisService service = new FixtureStockInfluencerAnalysisService(
		new FixtureRecentAnalysisSourceAdapter(JsonMapper.builder().build())
	);

	@Test
	void analyzesGrowthFixtureWithFourSentimentGroupsAndZeroCost() {
		var result = service.findAnalysis(new InfluencerProfileId("growth-lab")).orElseThrow();

		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(result.analyzedPostCount()).isEqualTo(10);
		assertThat(result.xApiRequestsThisCall()).isZero();
		assertThat(result.llmCallsThisCall()).isZero();
		assertThat(result.xApiRequestBudget()).isZero();
		assertThat(result.llmCallBudget()).isZero();
		assertThat(result.companies()).extracting(company -> company.overallSentiment())
			.contains("POSITIVE", "NEGATIVE", "NEUTRAL", "UNCERTAIN");
		assertThat(result.companies()).allSatisfy(company -> assertThat(company.evidence())
			.allSatisfy(evidence -> assertThat(evidence.sourceUrl().getScheme()).isEqualTo("urn")));
		assertThat(result.warnings()).contains("FIXTURE_DATA_NOT_LIVE");
	}

	@Test
	void keepsAnEmptyNegativeGroupPossibleForDividendFixture() {
		var result = service.findAnalysis(new InfluencerProfileId("dividend-harbor")).orElseThrow();

		assertThat(result.companies()).noneMatch(company -> "NEGATIVE".equals(company.overallSentiment()));
	}

	@Test
	void refusesLiveAndUnknownProfilesWithoutCallingExternalSystems() {
		assertThat(service.findAnalysis(new InfluencerProfileId("serenity"))).isEmpty();
		assertThat(service.findAnalysis(new InfluencerProfileId("missing"))).isEmpty();
	}
}
