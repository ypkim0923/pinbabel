package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisRunDetailResource(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	Long durationMs,
	boolean traceAvailable,
	String warningCode,
	String outcomeCode,
	String outcomeSummary,
	AnalysisRunMetricsResource metrics,
	InfluencerAnalysisReportResource report,
	List<AnalysisProgressEventResource> events
) {
	public AnalysisRunDetailResource {
		events = List.copyOf(events);
	}
}
