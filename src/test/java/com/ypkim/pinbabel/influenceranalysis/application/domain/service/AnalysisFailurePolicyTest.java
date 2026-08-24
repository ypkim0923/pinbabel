package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisFailureDecision;
import org.junit.jupiter.api.Test;

class AnalysisFailurePolicyTest {

	private final AnalysisFailurePolicy policy = new AnalysisFailurePolicy();

	@Test
	void preservesRecoverableXFailureActionsWithoutExposingProviderDetails() {
		var credits = policy.evaluate(wrapped(InfluencerAnalysisInternalCode.X_API_CREDITS_REQUIRED));
		var period = policy.evaluate(wrapped(InfluencerAnalysisInternalCode.X_POST_LIMIT_EXCEEDED));
		var rateLimit = policy.evaluate(wrapped(InfluencerAnalysisInternalCode.X_API_RATE_LIMITED));

		assertThat(credits.outcomeCode()).isEqualTo("X_API_CREDITS_REQUIRED");
		assertThat(credits.message()).contains("credit").doesNotContain("provider secret");
		assertThat(period.outcomeCode()).isEqualTo("X_PERIOD_TOO_BROAD");
		assertThat(period.message()).contains("기간");
		assertThat(rateLimit.outcomeCode()).isEqualTo("X_API_RATE_LIMITED");
		assertThat(rateLimit.message()).contains("잠시 후");
	}

	@Test
	void keepsUnexpectedFailuresGeneric() {
		var decision = policy.evaluate(new IllegalStateException("database password leaked"));

		assertThat(decision.outcomeCode()).isEqualTo("AGENT_EXECUTION_FAILED");
		assertThat(decision.message()).doesNotContain("password");
	}

	@Test
	void mapsHttpServiceAndAccessFailuresToRecoverableOutcomes() {
		assertThat(policy.evaluate(wrapped(InfluencerAnalysisInternalCode.X_API_SERVICE_UNAVAILABLE)).outcomeCode())
			.isEqualTo("X_API_TEMPORARILY_UNAVAILABLE");
		assertThat(policy.evaluate(wrapped(InfluencerAnalysisInternalCode.X_API_ACCESS_DENIED)).outcomeCode())
			.isEqualTo("X_CONFIGURATION_REQUIRED");
	}

	@Test
	void failureDecisionRejectsBlankValues() {
		assertThatThrownBy(() -> new AnalysisFailureDecision(
			" ", "message"
		)).isInstanceOf(InfluencerAnalysisException.class);
		assertThatThrownBy(() -> new AnalysisFailureDecision(
			"CODE", null
		)).isInstanceOf(InfluencerAnalysisException.class);
	}

	private RuntimeException wrapped(InfluencerAnalysisInternalCode code) {
		return new IllegalStateException("Embabel wrapper", new InfluencerAnalysisException(
			code, "provider secret detail"
		));
	}
}
