package com.ypkim.pinbabel.influenceranalysis.application.port.in.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisCorrelationId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import java.util.List;

public record AnalyzeInfluencerPostsResource(
	String runId,
	String correlationId,
	String status,
	String message,
	InfluencerAnalysisReport report,
	String disclaimer,
	boolean traceAvailable,
	List<String> warnings
) {

	public AnalyzeInfluencerPostsResource {
		warnings = List.copyOf(warnings);
	}

	public static AnalyzeInfluencerPostsResource from(
		AnalysisRunId runId,
		AnalysisCorrelationId correlationId,
		InfluencerAnalysisOutcome outcome,
		boolean traceAvailable,
		String traceWarning
	) {
		return new AnalyzeInfluencerPostsResource(
			runId.value(),
			correlationId.value(),
			outcome.status().name(),
			outcome.message(),
			outcome.report(),
			outcome.disclaimer(),
			traceAvailable,
			traceWarning == null ? List.of() : List.of(traceWarning)
		);
	}
}
