package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("web")
@PrimaryAdapter
public class SsrAnalysisFailurePresenter {

	public FailurePresentation present(String outcomeCode) {
		return switch (outcomeCode == null ? "" : outcomeCode) {
			case "X_API_CREDITS_REQUIRED" -> new FailurePresentation(
				"X Developer Console에서 credit을 확인한 뒤 다시 실행해 주세요.", true
			);
			case "X_API_RATE_LIMITED" -> new FailurePresentation(
				"X 요청 한도에 도달했습니다. 잠시 기다린 뒤 상태를 다시 확인해 주세요.", false
			);
			case "X_CONFIGURATION_REQUIRED" -> new FailurePresentation(
				"로컬 환경 변수와 실행 프로필을 확인해 주세요.", true
			);
			case "X_INFLUENCER_NOT_FOUND" -> new FailurePresentation(
				"고정 계정의 공개 포스트 접근 상태를 확인해 주세요.", false
			);
			case "EXECUTION_CAPACITY_EXCEEDED" -> new FailurePresentation(
				"진행 중인 분석이 끝난 뒤 다시 실행해 주세요.", true
			);
			case "X_API_TEMPORARILY_UNAVAILABLE", "LLM_TIMEOUT", "ANALYSIS_INTERRUPTED",
				"RECENT_X_ANALYSIS_EXECUTION_FAILED" -> new FailurePresentation(
					"일시적인 분석 장애입니다. 잠시 후 한 번만 다시 실행해 주세요.", true
				);
			default -> new FailurePresentation(
				"분석을 완료하지 못했습니다. 실행 참조를 확인하고 한 번만 다시 시도해 주세요.", true
			);
		};
	}

	public record FailurePresentation(String guidance, boolean retryAllowed) {
	}
}
