package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.net.URI;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class RecentCompanyEvidenceTest {

	private static final Instant PUBLISHED_AT = Instant.parse("2026-08-24T00:00:00Z");
	private static final URI SOURCE_URL = URI.create("https://x.com/example/status/post-1");

	@Test
	void rejectsAssessmentConfidenceOutsideUnitInterval() {
		assertFailure(
			() -> new RecentCompanyMentionAssessment(
				"post-1", "Microsoft", Sentiment.POSITIVE, "reason", Double.NaN
			),
			InfluencerAnalysisInternalCode.RECENT_MENTION_CONFIDENCE_INVALID
		);
	}

	@Test
	void rejectsMissingEvidencePostId() {
		assertFailure(
			() -> evidence(null, PUBLISHED_AT, SOURCE_URL, "excerpt", Sentiment.POSITIVE, "reason", 0.8),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_POST_ID_REQUIRED
		);
	}

	@Test
	void rejectsMissingEvidencePublicationTime() {
		assertFailure(
			() -> evidence("post-1", null, SOURCE_URL, "excerpt", Sentiment.POSITIVE, "reason", 0.8),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_PUBLISHED_AT_REQUIRED
		);
	}

	@Test
	void rejectsMissingEvidenceSourceUrl() {
		assertFailure(
			() -> evidence("post-1", PUBLISHED_AT, null, "excerpt", Sentiment.POSITIVE, "reason", 0.8),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_SOURCE_URL_REQUIRED
		);
	}

	@Test
	void rejectsMissingEvidenceExcerpt() {
		assertFailure(
			() -> evidence("post-1", PUBLISHED_AT, SOURCE_URL, " ", Sentiment.POSITIVE, "reason", 0.8),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_EXCERPT_REQUIRED
		);
	}

	@Test
	void rejectsMissingEvidenceSentiment() {
		assertFailure(
			() -> evidence("post-1", PUBLISHED_AT, SOURCE_URL, "excerpt", null, "reason", 0.8),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_SENTIMENT_REQUIRED
		);
	}

	@Test
	void rejectsMissingEvidenceRationale() {
		assertFailure(
			() -> evidence("post-1", PUBLISHED_AT, SOURCE_URL, "excerpt", Sentiment.POSITIVE, " ", 0.8),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_RATIONALE_REQUIRED
		);
	}

	@Test
	void rejectsEvidenceConfidenceOutsideUnitInterval() {
		assertFailure(
			() -> evidence("post-1", PUBLISHED_AT, SOURCE_URL, "excerpt", Sentiment.POSITIVE, "reason", 1.1),
			InfluencerAnalysisInternalCode.RECENT_EVIDENCE_CONFIDENCE_INVALID
		);
	}

	private RecentCompanyEvidence evidence(
		String postId,
		Instant publishedAt,
		URI sourceUrl,
		String excerpt,
		Sentiment sentiment,
		String rationale,
		double confidence
	) {
		return new RecentCompanyEvidence(
			postId, publishedAt, sourceUrl, excerpt, sentiment, rationale, confidence
		);
	}

	private void assertFailure(ThrowingCallable construction, InfluencerAnalysisInternalCode expectedCode) {
		assertThatThrownBy(construction)
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode()).isEqualTo(expectedCode)
			);
	}
}
