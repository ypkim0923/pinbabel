package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.net.URI;
import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RecentCompanyEvidence(
	String postId,
	Instant publishedAt,
	URI sourceUrl,
	String excerpt,
	Sentiment sentiment,
	String rationale,
	double confidence
) {

	public RecentCompanyEvidence {
		if (postId == null || postId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_POST_ID_REQUIRED,
				"Recent company evidence post identifier is required"
			);
		}
		if (publishedAt == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_PUBLISHED_AT_REQUIRED,
				"Recent company evidence publication time is required"
			);
		}
		if (sourceUrl == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_SOURCE_URL_REQUIRED,
				"Recent company evidence source URL is required"
			);
		}
		if (excerpt == null || excerpt.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_EXCERPT_REQUIRED,
				"Recent company evidence excerpt is required"
			);
		}
		if (sentiment == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_SENTIMENT_REQUIRED,
				"Recent company evidence sentiment is required"
			);
		}
		if (rationale == null || rationale.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_RATIONALE_REQUIRED,
				"Recent company evidence rationale is required"
			);
		}
		if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_EVIDENCE_CONFIDENCE_INVALID,
				"Recent company evidence confidence must be between zero and one"
			);
		}
		postId = postId.strip();
		excerpt = excerpt.strip();
		rationale = rationale.strip();
	}
}
