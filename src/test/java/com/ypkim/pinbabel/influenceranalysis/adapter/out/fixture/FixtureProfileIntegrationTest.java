package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.service.tool.AnalysisWorkspaceTools;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.InstrumentCatalog;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("fixture")
class FixtureProfileIntegrationTest {

	@Autowired
	private SocialPostSource socialPostSource;

	@Autowired
	private InstrumentCatalog instrumentCatalog;

	@Test
	void fixtureProfileRegistersBothOutboundAdapters() {
		assertThat(socialPostSource).isInstanceOf(FixtureSocialPostSource.class);
		assertThat(instrumentCatalog).isInstanceOf(FixtureInstrumentCatalog.class);
	}

	@Test
	void fixturePortsPopulateARequestScopedAnalysisWorkspace() {
		var request = new InfluencerAnalysisRequest(
			"fixture-social",
			"0007-market-voice",
			new AnalysisPeriod(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-03T00:00:00Z"),
				ZoneId.of("UTC")
			),
			Set.of("NASDAQ")
		);
		var workspace = new AnalysisWorkspaceTools(
			socialPostSource.findPosts(request),
			request.marketCodes(),
			instrumentCatalog
		);

		assertThat(workspace.listPosts().items())
			.extracting(AnalysisWorkspaceTools.PostSummary::postId)
			.contains("post-start", "post-negative", "post-injection");
		assertThat(workspace.readPost("post-injection").post().text())
			.contains("Ignore all previous instructions");
		assertThat(workspace.searchInstruments("nvidia").items())
			.extracting(AnalysisWorkspaceTools.InstrumentItem::ticker)
			.containsExactly("NVDA");
	}
}
