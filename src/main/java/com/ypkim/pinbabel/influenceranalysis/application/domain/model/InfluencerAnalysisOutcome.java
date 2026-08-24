package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

public record InfluencerAnalysisOutcome(
	Status status,
	InfluencerAnalysisReport report,
	String message,
	String disclaimer
) {

	public static final String DISCLAIMER =
		"This is an automated analysis of public SNS statements and is not investment advice.";

	public static InfluencerAnalysisOutcome completed(InfluencerAnalysisReport report) {
		return new InfluencerAnalysisOutcome(Status.COMPLETED, report, "Analysis completed", DISCLAIMER);
	}

	public static InfluencerAnalysisOutcome refused(AnalysisScopeDecision decision) {
		var status = decision.status() == AnalysisScopeDecision.Status.INCOMPLETE
			? Status.INCOMPLETE
			: Status.REFUSED;
		return new InfluencerAnalysisOutcome(status, null, decision.message(), DISCLAIMER);
	}

	public static InfluencerAnalysisOutcome failed(String message) {
		return new InfluencerAnalysisOutcome(Status.FAILED, null, message, DISCLAIMER);
	}

	public enum Status {
		COMPLETED,
		REFUSED,
		INCOMPLETE,
		FAILED
	}
}
