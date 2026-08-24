package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RecentCompanyMentionAssessment(
	String postId,
	String mention,
	Sentiment sentiment,
	String rationale,
	double confidence
) {

	public RecentCompanyMentionAssessment {
		if (postId == null || postId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTION_POST_ID_REQUIRED,
				"Recent company mention post identifier is required"
			);
		}
		if (mention == null || mention.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTION_TEXT_REQUIRED,
				"Recent company mention text is required"
			);
		}
		if (sentiment == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTION_SENTIMENT_REQUIRED,
				"Recent company mention sentiment is required"
			);
		}
		if (rationale == null || rationale.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTION_RATIONALE_REQUIRED,
				"Recent company mention rationale is required"
			);
		}
		if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTION_CONFIDENCE_INVALID,
				"Recent company mention confidence must be between zero and one"
			);
		}
		postId = postId.strip();
		mention = mention.strip();
		rationale = rationale.strip();
	}
}
