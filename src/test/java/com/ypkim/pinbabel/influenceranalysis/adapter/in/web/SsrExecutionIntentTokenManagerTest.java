package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SsrExecutionIntentTokenManagerTest {

	@Test
	void acceptsATokenOnlyOnceInTheIssuingSession() {
		var manager = new SsrExecutionIntentTokenManager(
			new SecureRandom(), Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC)
		);
		var session = new MockHttpSession();
		var token = manager.issue(session);

		assertThat(manager.consume(session, token)).isTrue();
		assertThat(manager.consume(session, token)).isFalse();
		assertThat(manager.consume(new MockHttpSession(), token)).isFalse();
	}

	@Test
	void replacingATokenInvalidatesThePreviousValue() {
		var manager = new SsrExecutionIntentTokenManager();
		var session = new MockHttpSession();
		var first = manager.issue(session);
		var second = manager.issue(session);

		assertThat(manager.consume(session, first)).isFalse();
		assertThat(manager.consume(session, second)).isTrue();
	}

	@Test
	void rejectsMissingAndOversizedTokensWithoutThrowing() {
		var manager = new SsrExecutionIntentTokenManager();
		var session = new MockHttpSession();
		manager.issue(session);

		assertThat(manager.consume(session, null)).isFalse();
		assertThat(manager.consume(session, "x".repeat(129))).isFalse();
	}
}
