package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;
import java.math.BigDecimal;
import java.util.List;

public record AnalysisRunMetricsResource(
	Long promptTokens,
	Long completionTokens,
	BigDecimal costUsd,
	List<String> models
) {
	public AnalysisRunMetricsResource {
		models = List.copyOf(models);
	}

	public static AnalysisRunMetricsResource from(AnalysisRunMetrics metrics) {
		return new AnalysisRunMetricsResource(
			metrics.promptTokens(), metrics.completionTokens(), metrics.costUsd(), metrics.models()
		);
	}
}
