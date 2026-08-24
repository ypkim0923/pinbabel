package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import java.time.Instant;

public record AnalysisRunSummaryResource(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	Instant startedAt,
	Long durationMs,
	boolean traceAvailable
) {
}
