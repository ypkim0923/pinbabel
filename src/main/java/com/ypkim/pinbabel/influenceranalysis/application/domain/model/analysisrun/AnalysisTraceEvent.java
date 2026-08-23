package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisTraceEvent(
	long sequence,
	String eventType,
	Instant occurredAt,
	String processId,
	String actionName,
	String toolName,
	String modelName,
	String providerName,
	Long durationMs,
	Boolean successful
) {

	public AnalysisTraceEvent {
		if (sequence < 1) {
			throw new IllegalArgumentException("Trace sequence must be positive");
		}
		if (eventType == null || eventType.isBlank()) {
			throw new IllegalArgumentException("Trace event type is required");
		}
		if (occurredAt == null) {
			throw new IllegalArgumentException("Trace event time is required");
		}
		if (durationMs != null && durationMs < 0) {
			throw new IllegalArgumentException("Trace duration must not be negative");
		}
	}
}
