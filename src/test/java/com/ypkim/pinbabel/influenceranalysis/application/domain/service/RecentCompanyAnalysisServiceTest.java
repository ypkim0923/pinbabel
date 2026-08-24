package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecentCompanyAnalysisServiceTest {

	private final RecentCompanyAnalysisService service = new RecentCompanyAnalysisService();

	@Test
	void keepsCompanyNameAndCashtagAsSeparateExactExpressions() {
		var posts = posts("Microsoft and $MSFT were both mentioned.");
		var assessments = new RecentCompanyMentionAssessments(List.of(
			assessment("Microsoft", Sentiment.POSITIVE),
			assessment("$MSFT", Sentiment.NEGATIVE)
		));

		var result = service.summarize(posts, assessments, Map.of(
			"post-1", "Microsoft and $MSFT were both mentioned."
		));

		assertThat(result.companies()).extracting(company -> company.mention())
			.containsExactly("Microsoft", "$MSFT");
		assertThat(result.companies().getFirst().overallSentiment()).isEqualTo(Sentiment.POSITIVE);
		assertThat(result.companies().getLast().overallSentiment()).isEqualTo(Sentiment.NEGATIVE);
	}

	@Test
	void ignoresHallucinatedMentionsAndUnknownPostIds() {
		var posts = posts("Microsoft was mentioned.");
		var assessments = new RecentCompanyMentionAssessments(List.of(
			assessment("Apple", Sentiment.POSITIVE),
			new RecentCompanyMentionAssessment("missing", "Microsoft", Sentiment.POSITIVE, "reason", 0.8)
		));

		var result = service.summarize(posts, assessments, Map.of(
			"post-1", "Microsoft was mentioned."
		));

		assertThat(result.companies()).isEmpty();
		assertThat(result.warnings()).containsExactly(
			"RECENT_MENTION_NOT_PRESENT_IN_ANALYZED_SOURCE",
			"RECENT_MENTION_POST_NOT_FOUND"
		);
	}

	@Test
	void countsOneVotePerPostAndMentionAndMakesConflictsUncertain() {
		var posts = posts("Microsoft was mentioned.");
		var assessments = new RecentCompanyMentionAssessments(List.of(
			assessment("Microsoft", Sentiment.POSITIVE),
			assessment("Microsoft", Sentiment.POSITIVE),
			assessment("Microsoft", Sentiment.NEGATIVE)
		));

		var result = service.summarize(posts, assessments, Map.of(
			"post-1", "Microsoft was mentioned."
		));

		assertThat(result.companies()).singleElement().satisfies(company -> {
			assertThat(company.overallSentiment()).isEqualTo(Sentiment.UNCERTAIN);
			assertThat(company.positiveCount()).isZero();
			assertThat(company.negativeCount()).isZero();
			assertThat(company.uncertainCount()).isEqualTo(1);
			assertThat(company.evidencePostIds()).containsExactly("post-1");
			assertThat(company.confidence()).isEqualTo(0.8);
			assertThat(company.evidence()).singleElement().satisfies(evidence -> {
				assertThat(evidence.sourceUrl()).isEqualTo(URI.create("https://x.com/example/status/post-1"));
				assertThat(evidence.publishedAt()).isEqualTo(Instant.parse("2026-08-24T00:00:00Z"));
				assertThat(evidence.excerpt()).contains("Microsoft");
				assertThat(evidence.rationale()).contains("Conflicting assessments");
			});
		});
		assertThat(result.warnings()).containsExactly(
			"RECENT_DUPLICATE_MENTION_IGNORED",
			"RECENT_CONFLICTING_DUPLICATE_MENTION"
		);
	}

	@Test
	void rejectsMentionThatExistsOnlyOutsideAnalyzedPrefix() {
		var posts = posts("visible prefix and Microsoft in the hidden tail");
		var assessments = new RecentCompanyMentionAssessments(List.of(
			assessment("Microsoft", Sentiment.POSITIVE)
		));

		var result = service.summarize(posts, assessments, Map.of("post-1", "visible prefix"));

		assertThat(result.companies()).isEmpty();
		assertThat(result.warnings()).containsExactly(
			"RECENT_MENTION_NOT_PRESENT_IN_ANALYZED_SOURCE"
		);
	}

	@Test
	void keepsHighestConfidenceDuplicateAndBoundsSourceExcerpt() {
		var source = "A".repeat(200) + "Microsoft" + "B".repeat(200);
		var posts = posts(source);
		var assessments = new RecentCompanyMentionAssessments(List.of(
			new RecentCompanyMentionAssessment("post-1", "Microsoft", Sentiment.POSITIVE, "lower", 0.4),
			new RecentCompanyMentionAssessment("post-1", "Microsoft", Sentiment.POSITIVE, "higher", 0.9)
		));

		var result = service.summarize(posts, assessments, Map.of("post-1", source));

		assertThat(result.companies()).singleElement().satisfies(company -> {
			assertThat(company.confidence()).isEqualTo(0.9);
			assertThat(company.evidence()).singleElement().satisfies(evidence -> {
				assertThat(evidence.rationale()).isEqualTo("higher");
				assertThat(evidence.excerpt()).contains("Microsoft").hasSize(280);
			});
		});
		assertThat(result.warnings()).containsExactly("RECENT_DUPLICATE_MENTION_IGNORED");
	}

	private RecentCompanyMentionAssessment assessment(String mention, Sentiment sentiment) {
		return new RecentCompanyMentionAssessment("post-1", mention, sentiment, "reason", 0.8);
	}

	private CollectedPosts posts(String text) {
		return new CollectedPosts(List.of(new CollectedPost(
			"post-1",
			"x",
			"author",
			Instant.parse("2026-08-24T00:00:00Z"),
			URI.create("https://x.com/example/status/post-1"),
			text,
			"x-api-v2",
			PostKind.ORIGINAL
		)));
	}
}
