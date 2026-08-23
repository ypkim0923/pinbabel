package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import java.time.Instant;

public record AnalysisRunSummaryResource(
	String runId,
	AnalysisRunStatus status,
	Instant createdAt,
	Instant startedAt,
	Long durationMs,
	boolean traceAvailable
) {
}
