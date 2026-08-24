package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisFailureDecision;
import java.util.concurrent.TimeoutException;
import org.jmolecules.ddd.annotation.Service;

@Service
public final class AnalysisFailurePolicy {

	public AnalysisFailureDecision evaluate(Throwable failure) {
		var domainFailure = findDomainFailure(failure);
		if (domainFailure == null) {
			if (hasCause(failure, InterruptedException.class)) {
				return new AnalysisFailureDecision("ANALYSIS_INTERRUPTED", "분석 요청이 중단되었습니다.");
			}
			if (hasCause(failure, TimeoutException.class)) {
				return new AnalysisFailureDecision(
					"LLM_TIMEOUT",
					"LLM 응답 제한 시간을 초과했습니다. 잠시 후 다시 시도해 주세요."
				);
			}
			return new AnalysisFailureDecision("AGENT_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
		}
		return switch (domainFailure.internalCode()) {
			case X_API_CREDITS_REQUIRED -> new AnalysisFailureDecision(
				"X_API_CREDITS_REQUIRED", "X Developer Console에서 API credit을 충전한 뒤 다시 시도해 주세요."
			);
			case X_POST_LIMIT_EXCEEDED, X_PAGINATION_LIMIT_EXCEEDED -> new AnalysisFailureDecision(
				"X_PERIOD_TOO_BROAD", "X 게시물이 분석 한도를 넘었습니다. 분석 기간을 더 좁혀 주세요."
			);
			case X_USERNAME_INVALID, X_USER_NOT_FOUND -> new AnalysisFailureDecision(
				"X_INFLUENCER_NOT_FOUND", "공개 X username을 확인한 뒤 다시 시도해 주세요."
			);
			case X_API_RATE_LIMITED -> new AnalysisFailureDecision(
				"X_API_RATE_LIMITED", "X API 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."
			);
			case X_USER_LOOKUP_REQUEST_FAILED, X_TIMELINE_REQUEST_FAILED,
				X_API_SERVICE_UNAVAILABLE -> new AnalysisFailureDecision(
				"X_API_TEMPORARILY_UNAVAILABLE", "X API 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."
			);
			case X_USER_LOOKUP_INTERRUPTED, X_TIMELINE_REQUEST_INTERRUPTED -> new AnalysisFailureDecision(
				"ANALYSIS_INTERRUPTED", "분석 요청이 중단되었습니다."
			);
			case X_BEARER_TOKEN_REQUIRED, X_API_ACCESS_DENIED -> new AnalysisFailureDecision(
				"X_CONFIGURATION_REQUIRED", "X_BEARER_TOKEN과 X 앱 접근 권한을 확인해 주세요."
			);
			default -> new AnalysisFailureDecision(
				"AGENT_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다."
			);
		};
	}

	private boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
		var current = failure;
		for (var depth = 0; current != null && depth < 16; depth++) {
			if (expectedType.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private InfluencerAnalysisException findDomainFailure(Throwable failure) {
		var current = failure;
		for (var depth = 0; current != null && depth < 16; depth++) {
			if (current instanceof InfluencerAnalysisException domainFailure) {
				return domainFailure;
			}
			current = current.getCause();
		}
		return null;
	}
}
