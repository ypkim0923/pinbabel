package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

import java.math.BigDecimal;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisRunMetrics(
	Long promptTokens,
	Long completionTokens,
	BigDecimal costUsd,
	List<String> models
) {

	public static final AnalysisRunMetrics EMPTY = new AnalysisRunMetrics(null, null, null, List.of());

	public AnalysisRunMetrics {
		if (promptTokens != null && promptTokens < 0) {
			throw new IllegalArgumentException("Prompt tokens must not be negative");
		}
		if (completionTokens != null && completionTokens < 0) {
			throw new IllegalArgumentException("Completion tokens must not be negative");
		}
		if (costUsd != null && costUsd.signum() < 0) {
			throw new IllegalArgumentException("Cost must not be negative");
		}
		models = models == null ? List.of() : models.stream().distinct().sorted().toList();
	}
}
