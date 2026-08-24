package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import java.time.Instant;

public record AnalysisProgressEventResource(
	long sequence,
	String eventType,
	Instant occurredAt,
	String actionName,
	String toolName,
	String modelName,
	Long durationMs,
	Boolean successful
) {

	public static AnalysisProgressEventResource from(AnalysisTraceEvent event) {
		return new AnalysisProgressEventResource(
			event.sequence(),
			event.eventType(),
			event.occurredAt(),
			event.actionName(),
			event.toolName(),
			event.modelName(),
			event.durationMs(),
			event.successful()
		);
	}
}
