package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisScopeDecision(
	Status status,
	InfluencerAnalysisRequest request,
	String message
) {

	public static AnalysisScopeDecision accepted(InfluencerAnalysisRequest request) {
		return new AnalysisScopeDecision(Status.ACCEPTED, request, "Analysis request accepted");
	}

	public static AnalysisScopeDecision rejected(String message) {
		return new AnalysisScopeDecision(Status.REJECTED, null, message);
	}

	public static AnalysisScopeDecision incomplete(String message) {
		return new AnalysisScopeDecision(Status.INCOMPLETE, null, message);
	}

	public enum Status {
		ACCEPTED,
		REJECTED,
		INCOMPLETE
	}
}
