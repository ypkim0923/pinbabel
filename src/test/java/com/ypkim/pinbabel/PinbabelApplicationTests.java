package com.ypkim.pinbabel;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.service.InfluencerAnalysisAgent;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.InstrumentCatalog;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PinbabelApplicationTests {

	private final ObjectProvider<SocialPostSource> socialPostSources;
	private final ObjectProvider<InstrumentCatalog> instrumentCatalogs;
	private final ObjectProvider<InfluencerAnalysisAgent> influencerAnalysisAgents;

	@Autowired
	PinbabelApplicationTests(
		ObjectProvider<SocialPostSource> socialPostSources,
		ObjectProvider<InstrumentCatalog> instrumentCatalogs,
		ObjectProvider<InfluencerAnalysisAgent> influencerAnalysisAgents
	) {
		this.socialPostSources = socialPostSources;
		this.instrumentCatalogs = instrumentCatalogs;
		this.influencerAnalysisAgents = influencerAnalysisAgents;
	}

	@Test
	void defaultProfileDoesNotExposeFixtureAdapters() {
		assertThat(socialPostSources.stream()).isEmpty();
		assertThat(instrumentCatalogs.stream()).isEmpty();
		assertThat(influencerAnalysisAgents.stream()).isEmpty();
	}

}
