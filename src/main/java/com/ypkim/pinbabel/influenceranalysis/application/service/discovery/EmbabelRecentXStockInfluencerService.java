package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisFailureDecision;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentXCompanyAnalysis;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisFailurePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture & x")
public class EmbabelRecentXStockInfluencerService implements AnalyzeRecentXStockInfluencerUseCase {

	static final Duration CACHE_TTL = Duration.ofMinutes(15);
	static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(1);
	static final int MAX_CACHE_ENTRIES = 20;
	private static final int MAXIMUM_X_API_REQUESTS = 2;
	private static final int MAXIMUM_LLM_CALLS = 1;

	private final Function<XAccountHandle, RecentXCompanyAnalysis> invocation;
	private final Clock clock;
	private final AnalysisFailurePolicy failurePolicy = new AnalysisFailurePolicy();
	private final LinkedHashMap<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
	private final LinkedHashMap<String, FailureEntry> failureCache = new LinkedHashMap<>(16, 0.75f, true);
	private final ConcurrentHashMap<String, CompletableFuture<RecentXCompanyAnalysis>> inFlight =
		new ConcurrentHashMap<>();

	@Autowired
	public EmbabelRecentXStockInfluencerService(AgentPlatform agentPlatform) {
		this(
			account -> AgentInvocation.builder(agentPlatform)
				.build(RecentXCompanyAnalysis.class)
				.invoke(account),
			Clock.systemUTC()
		);
	}

	EmbabelRecentXStockInfluencerService(
		Function<XAccountHandle, RecentXCompanyAnalysis> invocation,
		Clock clock
	) {
		this.invocation = invocation;
		this.clock = clock;
	}

	@Override
	public RecentMentionedCompaniesResource mentionedCompanies(String account) {
		var result = analyze(account);
		return new RecentMentionedCompaniesResource(
			result.status(),
			result.message(),
			result.account(),
			result.analyzedPostCount(),
			true,
			true,
			result.cacheHit(),
			result.xApiRequestsThisCall(),
			result.llmCallsThisCall(),
			MAXIMUM_X_API_REQUESTS,
			MAXIMUM_LLM_CALLS,
			result.durationMs(),
			result.companies(),
			result.warnings(),
			InfluencerAnalysisOutcome.DISCLAIMER
		);
	}

	@Override
	public RecentCompanySentimentResource companySentiment(String account) {
		var result = analyze(account);
		var positive = result.companies().stream()
			.filter(company -> company.positiveCount() > 0)
			.toList();
		var negative = result.companies().stream()
			.filter(company -> company.negativeCount() > 0)
			.toList();
		return new RecentCompanySentimentResource(
			result.status(),
			result.message(),
			result.account(),
			result.analyzedPostCount(),
			true,
			true,
			result.cacheHit(),
			result.xApiRequestsThisCall(),
			result.llmCallsThisCall(),
			MAXIMUM_X_API_REQUESTS,
			MAXIMUM_LLM_CALLS,
			result.durationMs(),
			positive,
			negative,
			result.warnings(),
			InfluencerAnalysisOutcome.DISCLAIMER
		);
	}

	private AnalysisResult analyze(String rawAccount) {
		var startedAt = clock.instant();
		final XAccountHandle account;
		try {
			account = new XAccountHandle(rawAccount);
		} catch (RuntimeException exception) {
			return failure(rawAccount, exception, 0, 0, false, elapsedMillis(startedAt));
		}
		var now = clock.instant();
		var cachedResult = cachedResult(account, now, startedAt);
		if (cachedResult != null) {
			return cachedResult;
		}

		var ownerFuture = new CompletableFuture<RecentXCompanyAnalysis>();
		var existingFuture = inFlight.putIfAbsent(account.username(), ownerFuture);
		if (existingFuture != null) {
			try {
				return success(existingFuture.join(), true, 0, 0, elapsedMillis(startedAt));
			} catch (CompletionException exception) {
				var cause = exception.getCause() instanceof RuntimeException runtimeException
					? runtimeException
					: exception;
				return failure(account.displayHandle(), cause, 0, 0, true, elapsedMillis(startedAt));
			}
		}
		try {
			var analysis = invocation.apply(account);
			cacheSuccess(account.username(), analysis, now);
			ownerFuture.complete(analysis);
			return success(
				analysis,
				false,
				analysis.xApiRequestCount(),
				analysis.llmCallCount(),
				elapsedMillis(startedAt)
			);
		} catch (Exception exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			ownerFuture.completeExceptionally(exception);
			cacheFailure(account.username(), exception, now);
			return failure(account.displayHandle(), exception, null, null, false, elapsedMillis(startedAt));
		} finally {
			inFlight.remove(account.username(), ownerFuture);
		}
	}

	private synchronized AnalysisResult cachedResult(
		XAccountHandle account,
		Instant now,
		Instant startedAt
	) {
		var cached = cache.get(account.username());
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return success(cached.analysis(), true, 0, 0, elapsedMillis(startedAt));
		}
		if (cached != null) {
			cache.remove(account.username());
		}
		var failed = failureCache.get(account.username());
		if (failed != null && now.isBefore(failed.expiresAt())) {
			return failure(account.displayHandle(), failed.decision(), 0, 0, true, elapsedMillis(startedAt));
		}
		if (failed != null) {
			failureCache.remove(account.username());
		}
		return null;
	}

	private synchronized void cacheSuccess(
		String username,
		RecentXCompanyAnalysis analysis,
		Instant now
	) {
		failureCache.remove(username);
		cache.put(username, new CacheEntry(analysis, now.plus(CACHE_TTL)));
		trimCache(cache);
	}

	private synchronized void cacheFailure(String username, Throwable exception, Instant now) {
		var decision = failurePolicy.evaluate(exception);
		failureCache.put(username, new FailureEntry(decision, now.plus(FAILURE_COOLDOWN)));
		trimCache(failureCache);
	}

	private AnalysisResult success(
		RecentXCompanyAnalysis analysis,
		boolean cacheHit,
		Integer xApiRequestsThisCall,
		Integer llmCallsThisCall,
		long durationMs
	) {
		var companies = analysis.companies().stream()
			.sorted(Comparator.comparing(summary -> summary.mention()))
			.map(summary -> new RecentCompanyResource(
				summary.mention(),
				summary.overallSentiment().name(),
				summary.positiveCount(),
				summary.negativeCount(),
				summary.neutralCount(),
					summary.uncertainCount(),
					summary.conflicting(),
					summary.confidence(),
					summary.evidence().stream()
						.map(evidence -> new RecentCompanyEvidenceResource(
							evidence.postId(),
							evidence.publishedAt(),
							evidence.sourceUrl(),
							evidence.excerpt(),
							evidence.sentiment().name(),
							evidence.rationale(),
							evidence.confidence()
						))
						.toList()
				))
			.toList();
		var message = cacheHit
			? "15분 캐시 결과를 사용해 외부 호출 없이 분석했습니다."
			: "최근 원본/인용 포스트 최대 10개를 분석했습니다.";
		return new AnalysisResult(
			"COMPLETED",
			message,
			analysis.account().displayHandle(),
			analysis.analyzedPostCount(),
			cacheHit,
			xApiRequestsThisCall,
			llmCallsThisCall,
			durationMs,
			companies,
			analysis.warnings()
		);
	}

	private AnalysisResult failure(
		String account,
		Throwable exception,
		Integer xApiRequestsThisCall,
		Integer llmCallsThisCall,
		boolean cacheHit,
		long durationMs
	) {
		return failure(
			account,
			failurePolicy.evaluate(exception),
			xApiRequestsThisCall,
			llmCallsThisCall,
			cacheHit,
			durationMs
		);
	}

	private AnalysisResult failure(
		String account,
		AnalysisFailureDecision decision,
		Integer xApiRequestsThisCall,
		Integer llmCallsThisCall,
		boolean cacheHit,
		long durationMs
	) {
		return new AnalysisResult(
			"FAILED",
			decision.message(),
			account == null ? "" : account,
			0,
			cacheHit,
			xApiRequestsThisCall,
			llmCallsThisCall,
			durationMs,
			List.of(),
			List.of(decision.outcomeCode())
		);
	}

	private long elapsedMillis(Instant startedAt) {
		return Math.max(0, Duration.between(startedAt, clock.instant()).toMillis());
	}

	private <T> void trimCache(LinkedHashMap<String, T> target) {
		while (target.size() > MAX_CACHE_ENTRIES) {
			var eldest = target.keySet().iterator().next();
			target.remove(eldest);
		}
	}

	private record CacheEntry(RecentXCompanyAnalysis analysis, Instant expiresAt) {
	}

	private record FailureEntry(
		AnalysisFailureDecision decision,
		Instant expiresAt
	) {
	}

	private record AnalysisResult(
		String status,
		String message,
		String account,
		int analyzedPostCount,
		boolean cacheHit,
		Integer xApiRequestsThisCall,
		Integer llmCallsThisCall,
		long durationMs,
		List<RecentCompanyResource> companies,
		List<String> warnings
	) {
	}
}
