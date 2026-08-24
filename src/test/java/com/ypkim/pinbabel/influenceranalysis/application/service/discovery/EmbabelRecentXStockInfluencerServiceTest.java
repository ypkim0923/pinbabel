package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyEvidence;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanySummary;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentXCompanyAnalysis;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EmbabelRecentXStockInfluencerServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void reusesOneAnalysisForMentionAndSentimentTools() {
		var invocations = new AtomicInteger();
		var service = new EmbabelRecentXStockInfluencerService(account -> {
			invocations.incrementAndGet();
			return analysis(account);
		}, CLOCK);

		var mentions = service.mentionedCompanies("@AleaBitOreddit");
		var sentiment = service.companySentiment("aleabitoreddit");

		assertThat(invocations).hasValue(1);
		assertThat(mentions.status()).isEqualTo("COMPLETED");
		assertThat(mentions.account()).isEqualTo("@aleabitoreddit");
		assertThat(mentions.commentsExcluded()).isTrue();
		assertThat(mentions.repostsExcluded()).isTrue();
		assertThat(mentions.cacheHit()).isFalse();
		assertThat(mentions.xApiRequestsThisCall()).isEqualTo(2);
		assertThat(mentions.llmCallsThisCall()).isEqualTo(1);
		assertThat(mentions.xApiRequestBudget()).isEqualTo(2);
		assertThat(mentions.llmCallBudget()).isEqualTo(1);
		assertThat(mentions.companies()).extracting(company -> company.mention())
			.containsExactly("$GOOGL", "NVIDIA");
		assertThat(mentions.companies().getFirst().evidence()).singleElement().satisfies(evidence -> {
			assertThat(evidence.sourceUrl()).isEqualTo(URI.create("https://x.com/example/status/post-1"));
			assertThat(evidence.rationale()).isEqualTo("source rationale");
			assertThat(evidence.confidence()).isEqualTo(0.9);
		});
		assertThat(sentiment.cacheHit()).isTrue();
		assertThat(sentiment.xApiRequestsThisCall()).isZero();
		assertThat(sentiment.llmCallsThisCall()).isZero();
		assertThat(sentiment.positiveCompanies()).extracting(company -> company.mention())
			.containsExactly("NVIDIA");
		assertThat(sentiment.negativeCompanies()).extracting(company -> company.mention())
			.containsExactly("$GOOGL");
	}

	@Test
	void refreshesOnlyAfterTheFifteenMinuteCacheTtl() {
		var clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
		var invocations = new AtomicInteger();
		var service = new EmbabelRecentXStockInfluencerService(account -> {
			invocations.incrementAndGet();
			return analysis(account);
		}, clock);

		service.mentionedCompanies("aleabitoreddit");
		clock.advance(Duration.ofMinutes(14));
		var cached = service.mentionedCompanies("aleabitoreddit");
		clock.advance(Duration.ofMinutes(2));
		var refreshed = service.mentionedCompanies("aleabitoreddit");

		assertThat(invocations).hasValue(2);
		assertThat(cached.cacheHit()).isTrue();
		assertThat(refreshed.cacheHit()).isFalse();
		assertThat(refreshed.xApiRequestsThisCall()).isEqualTo(2);
	}

	@Test
	void rejectsInvalidHandleBeforeAgentOrExternalInvocation() {
		var invocations = new AtomicInteger();
		var service = new EmbabelRecentXStockInfluencerService(account -> {
			invocations.incrementAndGet();
			return analysis(account);
		}, CLOCK);

		var result = service.mentionedCompanies("invalid/name");

		assertThat(invocations).hasValue(0);
		assertThat(result.status()).isEqualTo("FAILED");
		assertThat(result.companies()).isEmpty();
		assertThat(result.warnings()).containsExactly("X_INFLUENCER_NOT_FOUND");
		assertThat(result.message()).doesNotContain("invalid/name");
	}

	@Test
	void appliesFailureCooldownWithoutRepeatingPaidCalls() {
		var invocations = new AtomicInteger();
		var service = new EmbabelRecentXStockInfluencerService(account -> {
			invocations.incrementAndGet();
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_CREDITS_REQUIRED,
				"provider body must stay hidden"
			);
		}, CLOCK);

		var first = service.mentionedCompanies("aleabitoreddit");
		var second = service.mentionedCompanies("aleabitoreddit");

		assertThat(invocations).hasValue(1);
		assertThat(first.status()).isEqualTo("FAILED");
		assertThat(second.status()).isEqualTo("FAILED");
		assertThat(first.xApiRequestsThisCall()).isNull();
		assertThat(first.llmCallsThisCall()).isNull();
		assertThat(second.cacheHit()).isTrue();
		assertThat(second.xApiRequestsThisCall()).isZero();
		assertThat(second.llmCallsThisCall()).isZero();
		assertThat(first.warnings()).containsExactly("X_API_CREDITS_REQUIRED");
		assertThat(first.message()).doesNotContain("provider body");
	}

	@Test
	void convertsCheckedEmbabelTimeoutIntoSafeFailureResource() {
		var service = new EmbabelRecentXStockInfluencerService(
			account -> sneakyThrow(new ExecutionException(new TimeoutException("provider detail"))),
			CLOCK
		);

		var result = service.mentionedCompanies("aleabitoreddit");

		assertThat(result.status()).isEqualTo("FAILED");
		assertThat(result.warnings()).containsExactly("LLM_TIMEOUT");
		assertThat(result.message()).contains("LLM 응답 제한 시간");
		assertThat(result.message()).doesNotContain("provider detail");
	}

	@Test
	void coalescesConcurrentRequestsForTheSameAccount() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var invocations = new AtomicInteger();
		var service = new EmbabelRecentXStockInfluencerService(account -> {
			invocations.incrementAndGet();
			entered.countDown();
			try {
				release.await();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("test interrupted", exception);
			}
			return analysis(account);
		}, CLOCK);

		var first = CompletableFuture.supplyAsync(() -> service.mentionedCompanies("aleabitoreddit"));
		assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
		var second = CompletableFuture.supplyAsync(() -> service.companySentiment("aleabitoreddit"));
		release.countDown();

		assertThat(first.get(1, TimeUnit.SECONDS).cacheHit()).isFalse();
		assertThat(second.get(1, TimeUnit.SECONDS).cacheHit()).isTrue();
		assertThat(invocations).hasValue(1);
	}

	private RecentXCompanyAnalysis analysis(XAccountHandle account) {
		return new RecentXCompanyAnalysis(
			account,
			10,
			List.of(
				summary("NVIDIA", 1, 0),
				summary("$GOOGL", 0, 1)
			),
			List.of("X_RECENT_ORIGINAL_POSTS_LIMITED_TO_800_MOST_RECENT_POSTS"),
			2,
			1
		);
	}

	@SuppressWarnings("unchecked")
	private static <T, E extends Throwable> T sneakyThrow(Throwable failure) throws E {
		throw (E) failure;
	}

	private RecentCompanySummary summary(
		String mention,
		int positive,
		int negative
	) {
		return new RecentCompanySummary(
			mention,
			positive > 0 ? Sentiment.POSITIVE : Sentiment.NEGATIVE,
			positive,
			negative,
			0,
			0,
			false,
			List.of(new RecentCompanyEvidence(
				"post-1",
				Instant.parse("2026-08-24T00:00:00Z"),
				URI.create("https://x.com/example/status/post-1"),
				mention + " source excerpt",
				positive > 0 ? Sentiment.POSITIVE : Sentiment.NEGATIVE,
				"source rationale",
				0.9
			))
		);
	}

	private static final class MutableClock extends Clock {

		private Instant current;

		private MutableClock(Instant current) {
			this.current = current;
		}

		private void advance(Duration duration) {
			current = current.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current;
		}
	}
}
