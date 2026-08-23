package com.ypkim.pinbabel.influenceranalysis.application.domain.error;

public final class InfluencerAnalysisException extends RuntimeException {

	private final InfluencerAnalysisInternalCode internalCode;

	public InfluencerAnalysisException(
		InfluencerAnalysisInternalCode internalCode,
		String message
	) {
		super(message);
		this.internalCode = internalCode;
	}

	public InfluencerAnalysisException(
		InfluencerAnalysisInternalCode internalCode,
		String message,
		Throwable cause
	) {
		super(message, cause);
		this.internalCode = internalCode;
	}

	public InfluencerAnalysisInternalCode internalCode() {
		return internalCode;
	}
}
