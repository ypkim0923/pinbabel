package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.agent.core.AgentPlatform;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.RecentSocialPostSource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.service.InfluencerAnalysisAgent;
import com.ypkim.pinbabel.influenceranalysis.application.service.discovery.RecentXStockInfluencerAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "X_BEARER_TOKEN=test-token")
@ActiveProfiles({"fixture", "x"})
class XProfileIntegrationTest {

	@Autowired
	private SocialPostSource socialPostSource;

	@Autowired
	private RecentSocialPostSource recentSocialPostSource;

	@Autowired
	private AnalyzeRecentXStockInfluencerUseCase recentAnalysisUseCase;

	@Autowired
	private RecentXStockInfluencerAgent recentAgent;

	@Autowired
	private AgentPlatform agentPlatform;

	@Autowired
	private InfluencerAnalysisAgent influencerAnalysisAgent;

	@Test
	void xProfileReplacesOnlyTheFixtureSocialSource() {
		assertThat(socialPostSource).isInstanceOf(XSocialPostSource.class);
		assertThat(recentSocialPostSource).isSameAs(socialPostSource);
		assertThat(influencerAnalysisAgent).isNotNull();
		assertThat(recentAnalysisUseCase).isNotNull();
		assertThat(recentAgent).isNotNull();
		assertThat(agentPlatform.agents())
			.anyMatch(agent -> agent.getDescription().contains("ten most recent"));
	}
}
