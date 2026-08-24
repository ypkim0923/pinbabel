package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinbabelDiscoveryShellCommandsTest {

	@Test
	void rendersTheCuratedAccountAndCostTransparency() {
		var resource = new XStockInfluencerRecommendationsResource(
			"실시간 인기 순위가 아닙니다.",
			false,
			false,
			List.of(new XStockInfluencerRecommendationResource(
				"serenity", "x", "@aleabitoreddit", "Serenity", "사용자 지정", "USER_CURATED",
				"기술 성장주", "LIVE_X", "SE", "teal"
			))
		);
		var commands = new PinbabelDiscoveryShellCommands(() -> resource, new PinbabelCliRenderer());

		var output = commands.recommendXAccounts();

		assertThat(output)
			.contains("@aleabitoreddit", "Serenity", "USER_CURATED")
			.contains("xApiUsed: false", "llmUsed: false")
			.doesNotContain("Bearer", "token");
	}
}
