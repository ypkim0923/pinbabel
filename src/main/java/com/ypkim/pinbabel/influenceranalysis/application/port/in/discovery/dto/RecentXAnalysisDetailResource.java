package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

import java.time.Instant;

public record RecentXAnalysisDetailResource(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	Long durationMs,
	String outcomeCode,
	String outcomeSummary,
	RecentMentionedCompaniesResource result
) {
}
