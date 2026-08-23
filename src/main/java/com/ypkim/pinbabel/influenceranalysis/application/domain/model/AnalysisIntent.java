package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisIntent(
	TaskType taskType,
	String platform,
	String influencerId,
	String startInclusive,
	String endExclusive,
	String timezone,
	Set<String> marketCodes
) {

	public AnalysisIntent {
		marketCodes = marketCodes == null ? Set.of() : Set.copyOf(marketCodes);
	}

	public enum TaskType {
		ANALYZE_INFLUENCER_POSTS,
		INVESTMENT_ADVICE,
		PRICE_PREDICTION,
		OTHER
	}
}
