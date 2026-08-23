package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.Locale;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PostAssessment(
	String postId,
	String instrumentId,
	String ticker,
	Sentiment sentiment,
	String rationale
) {

	public PostAssessment {
		if (postId == null || postId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_POST_ID_REQUIRED,
				"Assessment post identifier is required"
			);
		}
		if (sentiment == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_SENTIMENT_REQUIRED,
				"Assessment sentiment is required"
			);
		}
		if (rationale == null || rationale.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_RATIONALE_REQUIRED,
				"Assessment rationale is required"
			);
		}

		var hasInstrumentId = instrumentId != null && !instrumentId.isBlank();
		var hasTicker = ticker != null && !ticker.isBlank();
		if (hasInstrumentId != hasTicker) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_CANONICAL_PAIR_REQUIRED,
				"Assessment instrument identifier and ticker must be provided together"
			);
		}
		if (!hasInstrumentId && sentiment != Sentiment.UNCERTAIN) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_CANONICAL_INSTRUMENT_REQUIRED,
				"A canonical instrument is required for a directional assessment"
			);
		}

		postId = postId.strip();
		instrumentId = hasInstrumentId ? instrumentId.strip() : null;
		ticker = hasTicker ? ticker.strip().toUpperCase(Locale.ROOT) : null;
		rationale = rationale.strip();
	}
}
