package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileSource;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.StockInfluencerProfile;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.StockInfluencerProfileCatalog;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StockInfluencerRecommendationServiceTest {

	private final StockInfluencerRecommendationService service = new StockInfluencerRecommendationService(
		new TestCatalog()
	);

	@Test
	void returnsTenCuratedProfilesWithoutExternalCalls() {
		var result = service.recommend();

		assertThat(result.xApiUsed()).isFalse();
		assertThat(result.llmUsed()).isFalse();
		assertThat(result.message()).contains("실험용");
		assertThat(result.accounts()).hasSize(10);
		assertThat(result.accounts().getFirst()).satisfies(account -> {
			assertThat(account.profileId()).isEqualTo("serenity");
			assertThat(account.handle()).isEqualTo("@aleabitoreddit");
			assertThat(account.displayName()).isEqualTo("Serenity");
			assertThat(account.selectionBasis()).isEqualTo("USER_CURATED");
			assertThat(account.sourceType()).isEqualTo("LIVE_X");
		});
	}

	@Test
	void returnsAnImmutableDeterministicRecommendationList() {
		var first = service.recommend();
		var second = service.recommend();

		assertThat(second).isEqualTo(first);
		assertThat(first.accounts()).isUnmodifiable();
	}

	private static final class TestCatalog implements StockInfluencerProfileCatalog {
		private final List<StockInfluencerProfile> profiles = IntStream.range(0, 10)
			.mapToObj(index -> new StockInfluencerProfile(
				new InfluencerProfileId(index == 0 ? "serenity" : "fixture-" + index),
				new XAccountHandle(index == 0 ? "@aleabitoreddit" : "@fixture" + index),
				index == 0 ? "Serenity" : "Fixture " + index,
				index == 0 ? "사용자가 지정한 계정" : "Fixture account",
				"테스트 관점",
				index == 0 ? InfluencerProfileSource.LIVE_X : InfluencerProfileSource.FIXTURE,
				index == 0 ? "SE" : "F" + index,
				"teal"
			))
			.toList();

		@Override
		public List<StockInfluencerProfile> findAll() {
			return profiles;
		}

		@Override
		public Optional<StockInfluencerProfile> findById(InfluencerProfileId profileId) {
			return profiles.stream().filter(profile -> profile.id().equals(profileId)).findFirst();
		}
	}
}
