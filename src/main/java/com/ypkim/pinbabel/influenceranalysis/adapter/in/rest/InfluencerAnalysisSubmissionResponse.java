package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record InfluencerAnalysisSubmissionResponse(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	String outcomeCode,
	String outcomeSummary
) {
	static InfluencerAnalysisSubmissionResponse from(AnalysisSubmissionResource resource) {
		return new InfluencerAnalysisSubmissionResponse(
			resource.runId(), resource.correlationId(), resource.status(), resource.createdAt(),
			resource.outcomeCode(), resource.outcomeSummary()
		);
	}
}
