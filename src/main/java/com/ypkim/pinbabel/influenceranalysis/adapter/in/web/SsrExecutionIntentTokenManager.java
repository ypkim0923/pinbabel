package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("web")
@PrimaryAdapter
public class SsrExecutionIntentTokenManager {

	static final Duration TOKEN_TTL = Duration.ofMinutes(10);
	private static final String SESSION_ATTRIBUTE = SsrExecutionIntentTokenManager.class.getName() + ".token";
	private final SecureRandom secureRandom;
	private final Clock clock;

	public SsrExecutionIntentTokenManager() {
		this(new SecureRandom(), Clock.systemUTC());
	}

	SsrExecutionIntentTokenManager(SecureRandom secureRandom, Clock clock) {
		this.secureRandom = secureRandom;
		this.clock = clock;
	}

	public String issue(HttpSession session) {
		var bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		synchronized (session) {
			session.setAttribute(SESSION_ATTRIBUTE, new StoredToken(value, clock.instant().plus(TOKEN_TTL)));
		}
		return value;
	}

	public boolean consume(HttpSession session, String suppliedToken) {
		if (suppliedToken == null || suppliedToken.isBlank() || suppliedToken.length() > 128) {
			return false;
		}
		synchronized (session) {
			var stored = (StoredToken) session.getAttribute(SESSION_ATTRIBUTE);
			if (stored == null || !clock.instant().isBefore(stored.expiresAt())) {
				session.removeAttribute(SESSION_ATTRIBUTE);
				return false;
			}
			var matches = MessageDigest.isEqual(
				stored.value().getBytes(StandardCharsets.US_ASCII),
				suppliedToken.getBytes(StandardCharsets.US_ASCII)
			);
			if (matches) {
				session.removeAttribute(SESSION_ATTRIBUTE);
			}
			return matches;
		}
	}

	private record StoredToken(String value, Instant expiresAt) {
	}
}
