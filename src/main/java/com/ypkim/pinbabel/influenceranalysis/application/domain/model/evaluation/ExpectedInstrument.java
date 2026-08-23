package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record ExpectedInstrument(String instrumentId, Sentiment sentiment, List<String> evidencePostIds) {

	public ExpectedInstrument {
		if (instrumentId == null || instrumentId.isBlank()) {
			throw new IllegalArgumentException("Expected instrument identifier is required");
		}
		if (sentiment == null) {
			throw new IllegalArgumentException("Expected sentiment is required");
		}
		if (evidencePostIds == null || evidencePostIds.isEmpty()) {
			throw new IllegalArgumentException("Expected evidence post identifiers are required");
		}
		evidencePostIds = List.copyOf(evidencePostIds);
		if (evidencePostIds.stream().anyMatch(id -> id == null || id.isBlank())) {
			throw new IllegalArgumentException("Expected evidence post identifier must not be blank");
		}
		if (evidencePostIds.stream().distinct().count() != evidencePostIds.size()) {
			throw new IllegalArgumentException("Expected evidence post identifiers must be unique");
		}
	}
}
