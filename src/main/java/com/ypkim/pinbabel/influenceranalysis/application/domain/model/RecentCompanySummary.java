package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RecentCompanySummary(
	String mention,
	Sentiment overallSentiment,
	int positiveCount,
	int negativeCount,
	int neutralCount,
	int uncertainCount,
	boolean conflicting,
	List<RecentCompanyEvidence> evidence
) {

	public RecentCompanySummary {
		if (mention == null || mention.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_COMPANY_MENTION_REQUIRED,
				"Recent company summary mention is required"
			);
		}
		if (overallSentiment == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_COMPANY_SENTIMENT_REQUIRED,
				"Recent company summary sentiment is required"
			);
		}
		if (positiveCount < 0 || negativeCount < 0 || neutralCount < 0 || uncertainCount < 0) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_COMPANY_COUNTS_INVALID,
				"Recent company summary counts cannot be negative"
			);
		}
		if (evidence == null || evidence.isEmpty()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_COMPANY_EVIDENCE_REQUIRED,
				"Recent company summary evidence is required"
			);
		}
		mention = mention.strip();
		evidence = List.copyOf(evidence);
	}

	public double confidence() {
		return evidence.stream()
			.mapToDouble(RecentCompanyEvidence::confidence)
			.average()
			.orElse(0.0);
	}

	public List<String> evidencePostIds() {
		return evidence.stream()
			.map(RecentCompanyEvidence::postId)
			.distinct()
			.toList();
	}
}
