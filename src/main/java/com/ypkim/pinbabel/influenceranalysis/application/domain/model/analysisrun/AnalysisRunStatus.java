package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

public enum AnalysisRunStatus {
	CREATED,
	RUNNING,
	COMPLETED,
	FAILED,
	REJECTED;

	public boolean terminal() {
		return this == COMPLETED || this == FAILED || this == REJECTED;
	}
}
