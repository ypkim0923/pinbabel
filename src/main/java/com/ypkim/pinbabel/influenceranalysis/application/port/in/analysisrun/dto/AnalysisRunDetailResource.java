package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import java.time.Instant;
import java.util.List;

public record AnalysisRunDetailResource(
	String runId,
	AnalysisRunStatus status,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	Long durationMs,
	boolean traceAvailable,
	String warningCode,
	String outcomeCode,
	String outcomeSummary,
	AnalysisRunMetrics metrics,
	InfluencerAnalysisReport report,
	List<AnalysisTraceEvent> events
) {
	public AnalysisRunDetailResource {
		events = List.copyOf(events);
	}
}
