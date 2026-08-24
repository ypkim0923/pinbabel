package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.FixtureRecentAnalysisSource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.dto.FixtureRecentAnalysisScenario;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("fixture")
@SecondaryAdapter
public class FixtureRecentAnalysisSourceAdapter implements FixtureRecentAnalysisSource {

	private static final String DEFAULT_FIXTURE =
		"fixtures/influenceranalysis/stock-influencer-recent-analysis-v1.json";
	private static final Instant REFERENCE_TIME = Instant.parse("2026-08-24T09:00:00Z");
	private final List<FixtureRecentAnalysisScenario> scenarios;

	@Autowired
	public FixtureRecentAnalysisSourceAdapter(ObjectMapper objectMapper) {
		this(objectMapper, new ClassPathResource(DEFAULT_FIXTURE));
	}

	FixtureRecentAnalysisSourceAdapter(ObjectMapper objectMapper, Resource fixture) {
		this.scenarios = load(objectMapper, fixture);
	}

	@Override
	public Optional<FixtureRecentAnalysisScenario> findByProfileId(InfluencerProfileId profileId) {
		return scenarios.stream().filter(scenario -> scenario.profileId().equals(profileId)).findFirst();
	}

	private List<FixtureRecentAnalysisScenario> load(ObjectMapper objectMapper, Resource fixture) {
		try (var input = fixture.getInputStream()) {
			var document = objectMapper.readValue(input, FixtureDocument.class);
			if (document == null || document.version() != 1 || document.profiles() == null
				|| document.profiles().size() != 9) {
				throw new IllegalArgumentException("Recent analysis fixture must contain nine profiles");
			}
			var mapped = document.profiles().stream().map(this::toScenario).toList();
			if (mapped.stream().map(scenario -> scenario.profileId().value()).distinct().count() != 9) {
				throw new IllegalArgumentException("Recent analysis fixture profile identifiers must be unique");
			}
			return List.copyOf(mapped);
		}
		catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Unable to read recent analysis fixture", exception);
		}
	}

	private FixtureRecentAnalysisScenario toScenario(ProfileScenarioDocument document) {
		if (document.posts() == null || document.posts().size() != 10) {
			throw new IllegalArgumentException("Every fixture profile must contain ten posts");
		}
		var profileId = new InfluencerProfileId(document.profileId());
		var account = new XAccountHandle(document.handle());
		var posts = new java.util.ArrayList<CollectedPost>();
		var assessments = new java.util.ArrayList<RecentCompanyMentionAssessment>();
		var analyzedText = new LinkedHashMap<String, String>();
		for (var index = 0; index < document.posts().size(); index++) {
			var item = document.posts().get(index);
			var postId = document.profileId() + "-" + (index + 1);
			var text = item.text();
			posts.add(new CollectedPost(
				postId,
				"fixture",
				account.username(),
				REFERENCE_TIME.minus(index, ChronoUnit.DAYS),
				URI.create("urn:pinbabel:fixture:" + document.profileId() + ":post:" + (index + 1)),
				text,
				"PINBABEL_FIXTURE",
				PostKind.ORIGINAL
			));
			assessments.add(new RecentCompanyMentionAssessment(
				postId,
				item.mention(),
				Sentiment.valueOf(item.sentiment()),
				item.rationale(),
				item.confidence()
			));
			analyzedText.put(postId, text);
		}
		return new FixtureRecentAnalysisScenario(
			profileId,
			account,
			new CollectedPosts(posts, List.of("FIXTURE_DATA_NOT_LIVE")),
			new RecentCompanyMentionAssessments(assessments),
			analyzedText
		);
	}

	private record FixtureDocument(int version, List<ProfileScenarioDocument> profiles) {
	}

	private record ProfileScenarioDocument(String profileId, String handle, List<PostDocument> posts) {
	}

	private record PostDocument(
		String mention,
		String sentiment,
		String text,
		String rationale,
		double confidence
	) {
	}
}
