package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("fixture")
@SecondaryAdapter
public class FixtureSocialPostSource implements SocialPostSource {

	private static final String DEFAULT_FIXTURE = "fixtures/influenceranalysis/posts.json";
	private static final Comparator<CollectedPost> POST_ORDER = Comparator
		.comparing(CollectedPost::publishedAt)
		.thenComparing(CollectedPost::postId);

	private final ObjectMapper objectMapper;
	private final Resource fixture;

	@Autowired
	public FixtureSocialPostSource(ObjectMapper objectMapper) {
		this(objectMapper, new ClassPathResource(DEFAULT_FIXTURE));
	}

	FixtureSocialPostSource(ObjectMapper objectMapper, Resource fixture) {
		this.objectMapper = objectMapper;
		this.fixture = fixture;
	}

	@Override
	public CollectedPosts findPosts(InfluencerAnalysisRequest request) {
		try (var input = fixture.getInputStream()) {
			var postsById = new LinkedHashMap<String, CollectedPost>();
			Arrays.stream(objectMapper.readValue(input, PostFixture[].class))
				.filter(item -> item.platform().equalsIgnoreCase(request.platform()))
				.filter(item -> item.authorId().equals(request.influencerId()))
				.map(this::toDomain)
				.filter(post -> request.period().contains(post.publishedAt()))
				.sorted(POST_ORDER)
				.forEach(post -> postsById.putIfAbsent(post.postId(), post));
			return new CollectedPosts(postsById.values().stream().toList());
		} catch (IOException | JacksonException | IllegalArgumentException | InfluencerAnalysisException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_FIXTURE_READ_FAILED,
				"Unable to read the social post fixture",
				exception
			);
		}
	}

	private CollectedPost toDomain(PostFixture fixturePost) {
		return new CollectedPost(
			fixturePost.postId(),
			fixturePost.platform().toLowerCase(Locale.ROOT),
			fixturePost.authorId(),
			Instant.parse(fixturePost.publishedAt()),
			URI.create(fixturePost.url()),
			fixturePost.text(),
			fixturePost.source(),
			PostKind.valueOf(fixturePost.kind().toUpperCase(Locale.ROOT))
		);
	}

	private record PostFixture(
		String postId,
		String platform,
		String authorId,
		String publishedAt,
		String url,
		String text,
		String source,
		String kind
	) {
	}
}
