package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FixtureStockInfluencerCatalogTest {

	private final FixtureStockInfluencerCatalog catalog = new FixtureStockInfluencerCatalog(
		JsonMapper.builder().build()
	);

	@Test
	void loadsTenOrderedProfilesWithOneLiveAccount() {
		var profiles = catalog.findAll();

		assertThat(profiles).hasSize(10).isUnmodifiable();
		assertThat(profiles.getFirst().id().value()).isEqualTo("serenity");
		assertThat(profiles).filteredOn(profile -> profile.source() == InfluencerProfileSource.LIVE_X)
			.singleElement().extracting(profile -> profile.handle().displayHandle()).isEqualTo("@aleabitoreddit");
		assertThat(profiles).extracting(profile -> profile.id().value()).doesNotHaveDuplicates();
		assertThat(profiles).extracting(profile -> profile.handle().username()).doesNotHaveDuplicates();
	}

	@Test
	void findsAProfileByStableIdentifier() {
		assertThat(catalog.findById(new InfluencerProfileId("growth-lab")))
			.get().extracting(profile -> profile.displayName()).isEqualTo("Pin Growth Lab");
	}
}
