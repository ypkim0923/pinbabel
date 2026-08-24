package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.http.HttpStatus;

@PrimaryAdapter
public final class SsrRequestException extends RuntimeException {

	private final InfluencerAnalysisInternalCode internalCode;
	private final HttpStatus status;

	private SsrRequestException(
		InfluencerAnalysisInternalCode internalCode,
		HttpStatus status,
		String message
	) {
		super(message);
		this.internalCode = internalCode;
		this.status = status;
	}

	public static SsrRequestException invalidProfileId() {
		return new SsrRequestException(
			InfluencerAnalysisInternalCode.SSR_PROFILE_ID_INVALID,
			HttpStatus.BAD_REQUEST,
			"프로필 식별자가 올바르지 않습니다."
		);
	}

	public static SsrRequestException profileNotFound() {
		return new SsrRequestException(
			InfluencerAnalysisInternalCode.SSR_PROFILE_NOT_FOUND,
			HttpStatus.NOT_FOUND,
			"요청한 인플루언서 프로필을 찾을 수 없습니다."
		);
	}

	public static SsrRequestException invalidRunId() {
		return new SsrRequestException(
			InfluencerAnalysisInternalCode.SSR_RUN_ID_INVALID,
			HttpStatus.BAD_REQUEST,
			"분석 실행 식별자가 올바르지 않습니다."
		);
	}

	public static SsrRequestException runNotFound() {
		return new SsrRequestException(
			InfluencerAnalysisInternalCode.SSR_RUN_NOT_FOUND,
			HttpStatus.NOT_FOUND,
			"분석 실행을 찾을 수 없습니다."
		);
	}

	public static SsrRequestException runAccountMismatch() {
		return new SsrRequestException(
			InfluencerAnalysisInternalCode.SSR_RUN_ACCOUNT_MISMATCH,
			HttpStatus.NOT_FOUND,
			"이 프로필에서 확인할 수 없는 분석 실행입니다."
		);
	}

	public static SsrRequestException invalidExecutionToken() {
		return new SsrRequestException(
			InfluencerAnalysisInternalCode.SSR_EXECUTION_TOKEN_INVALID,
			HttpStatus.FORBIDDEN,
			"분석 실행 요청이 만료되었거나 이미 사용되었습니다."
		);
	}

	public InfluencerAnalysisInternalCode internalCode() {
		return internalCode;
	}

	public HttpStatus status() {
		return status;
	}
}
