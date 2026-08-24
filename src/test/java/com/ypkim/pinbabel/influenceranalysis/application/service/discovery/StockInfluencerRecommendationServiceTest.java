package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockInfluencerRecommendationServiceTest {

	private final StockInfluencerRecommendationService service = new StockInfluencerRecommendationService();

	@Test
	void returnsTheUserCuratedSerenityAccountWithoutExternalCalls() {
		var result = service.recommend();

		assertThat(result.xApiUsed()).isFalse();
		assertThat(result.llmUsed()).isFalse();
		assertThat(result.message()).contains("사용자가 지정한");
		assertThat(result.accounts()).singleElement().satisfies(account -> {
			assertThat(account.platform()).isEqualTo("x");
			assertThat(account.handle()).isEqualTo("@aleabitoreddit");
			assertThat(account.displayName()).isEqualTo("Serenity");
			assertThat(account.selectionBasis()).isEqualTo("USER_CURATED");
			assertThat(account.reason()).contains("사용자가 지정한");
		});
	}

	@Test
	void returnsAnImmutableDeterministicRecommendationList() {
		var first = service.recommend();
		var second = service.recommend();

		assertThat(second).isEqualTo(first);
		assertThat(first.accounts()).isUnmodifiable();
	}
}
