package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class FixtureSocialPostSourceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void loadsPostsWithinPeriodDeduplicatedAndStablyOrdered() {
		var source = new FixtureSocialPostSource(objectMapper);
		var request = request(
			Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-01-03T00:00:00Z")
		);

		var result = source.findPosts(request);

		assertThat(result.posts())
			.extracting(post -> post.postId())
			.containsExactly("post-start", "post-negative", "post-neutral", "post-mixed", "post-ambiguous", "post-injection");
		assertThat(result.posts()).extracting(post -> post.postId()).doesNotHaveDuplicates();
		assertThat(result.posts().getFirst().publishedAt())
			.isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
		assertThat(result.posts()).allSatisfy(post -> {
			assertThat(post.url()).isNotNull();
			assertThat(post.source()).isEqualTo("pinbabel-fixture");
		});
	}

	@Test
	void preservesPromptInjectionAsUntrustedPostContent() {
		var source = new FixtureSocialPostSource(objectMapper);

		var result = source.findPosts(request(
			Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-01-03T00:00:00Z")
		));

		assertThat(result.posts())
			.filteredOn(post -> post.postId().equals("post-injection"))
			.singleElement()
			.extracting(post -> post.text())
			.asString()
			.contains("Ignore all previous instructions");
	}

	@Test
	void missingFixtureIsTranslatedWithSourceInternalCode() {
		var source = new FixtureSocialPostSource(
			objectMapper,
			new ClassPathResource("fixtures/influenceranalysis/does-not-exist.json")
		);

		assertThatThrownBy(() -> source.findPosts(request(
			Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-01-03T00:00:00Z")
		)))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.POST_FIXTURE_READ_FAILED));
	}

	@Test
	void malformedFixtureIsTranslatedWithSourceInternalCode() {
		var resource = new ByteArrayResource("{not-json".getBytes(StandardCharsets.UTF_8));
		var source = new FixtureSocialPostSource(objectMapper, resource);

		assertThatThrownBy(() -> source.findPosts(request(
			Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-01-03T00:00:00Z")
		)))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.POST_FIXTURE_READ_FAILED));
	}

	private InfluencerAnalysisRequest request(Instant start, Instant end) {
		return new InfluencerAnalysisRequest(
			"fixture-social",
			"0007-market-voice",
			new AnalysisPeriod(start, end, ZoneId.of("UTC")),
			Set.of("NASDAQ")
		);
	}
}
