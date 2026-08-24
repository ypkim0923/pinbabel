package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.http.HttpStatus;

@PrimaryAdapter
public final class PinbabelApiException extends RuntimeException {
	private final HttpStatus status;
	private final String publicCode;
	private final InfluencerAnalysisInternalCode internalCode;

	PinbabelApiException(HttpStatus status, String publicCode, InfluencerAnalysisInternalCode internalCode, String message) {
		super(message);
		this.status = status;
		this.publicCode = publicCode;
		this.internalCode = internalCode;
	}

	public static PinbabelApiException invalidRunId() {
		return new PinbabelApiException(HttpStatus.BAD_REQUEST, "INVALID_RUN_ID",
			InfluencerAnalysisInternalCode.API_RUN_ID_INVALID, "runId must be a UUID");
	}

	static PinbabelApiException invalidRequest() {
		return new PinbabelApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
			InfluencerAnalysisInternalCode.API_REQUEST_INVALID, "Request validation failed");
	}

	static PinbabelApiException bodyTooLarge() {
		return new PinbabelApiException(HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_BODY_TOO_LARGE",
			InfluencerAnalysisInternalCode.API_BODY_TOO_LARGE, "Request body exceeds 16 KiB");
	}

	static PinbabelApiException unexpected() {
		return new PinbabelApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
			InfluencerAnalysisInternalCode.API_UNEXPECTED_FAILURE, "Unexpected server error");
	}

	public static PinbabelApiException runNotFound() {
		return new PinbabelApiException(HttpStatus.NOT_FOUND, "ANALYSIS_RUN_NOT_FOUND",
			InfluencerAnalysisInternalCode.API_RUN_NOT_FOUND, "Analysis run was not found");
	}

	public HttpStatus status() { return status; }
	public String publicCode() { return publicCode; }
	public InfluencerAnalysisInternalCode internalCode() { return internalCode; }
}
