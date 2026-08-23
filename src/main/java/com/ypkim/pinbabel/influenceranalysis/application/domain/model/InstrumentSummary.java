package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record InstrumentSummary(
	String instrumentId,
	String ticker,
	String displayName,
	Sentiment overallSentiment,
	int positiveCount,
	int negativeCount,
	int neutralCount,
	int uncertainCount,
	boolean conflicting,
	List<String> evidencePostIds
) {

	public InstrumentSummary {
		evidencePostIds = List.copyOf(evidencePostIds);
	}
}
