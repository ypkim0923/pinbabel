package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AssessmentEvidence(
	String postId,
	String platform,
	String authorId,
	Instant publishedAt,
	String url,
	String source,
	String instrumentId,
	String ticker,
	Sentiment sentiment,
	String excerpt,
	String rationale
) {
}
