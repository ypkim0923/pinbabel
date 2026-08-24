package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

import java.net.URI;
import java.time.Instant;

public record RecentCompanyEvidenceResource(
	String postId,
	Instant publishedAt,
	URI sourceUrl,
	String excerpt,
	String sentiment,
	String rationale,
	double confidence
) {
}
