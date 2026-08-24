package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "PINBABEL_X_LIVE_TEST", matches = "true")
class XApiLiveSmokeTest {

	@Test
	void readsRecentPublicPostsWithTheConfiguredBearerToken() {
		var now = Instant.now().minus(Duration.ofMinutes(1));
		var environment = new MockEnvironment()
			.withProperty("X_BEARER_TOKEN", System.getenv("X_BEARER_TOKEN"));
		var source = new XSocialPostSource(new ObjectMapper(), environment);
		var request = new InfluencerAnalysisRequest(
			"x",
			"XDevelopers",
			new AnalysisPeriod(now.minus(Duration.ofDays(2)), now, ZoneId.of("UTC")),
			Set.of("NASDAQ")
		);

		var result = source.findPosts(request);

		assertThat(result).isNotNull();
		assertThat(result.posts()).allSatisfy(post -> {
			assertThat(post.platform()).isEqualTo("x");
			assertThat(post.source()).isEqualTo("x-api-v2");
		});
	}
}
