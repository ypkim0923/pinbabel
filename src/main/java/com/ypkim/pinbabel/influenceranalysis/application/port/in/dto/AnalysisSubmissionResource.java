package com.ypkim.pinbabel.influenceranalysis.application.port.in.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import java.time.Instant;

public record AnalysisSubmissionResource(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	String outcomeCode,
	String outcomeSummary
) {

	public static AnalysisSubmissionResource from(AnalysisRun run) {
		return new AnalysisSubmissionResource(
			run.id().value(),
			run.correlationId().value(),
			run.status().name(),
			run.createdAt(),
			run.outcomeCode(),
			run.outcomeSummary()
		);
	}
}
