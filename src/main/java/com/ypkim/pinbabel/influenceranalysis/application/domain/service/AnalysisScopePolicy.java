package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisIntent;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisScopeDecision;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import org.jmolecules.ddd.annotation.Service;

@Service
public final class AnalysisScopePolicy {

	public static final int MAX_INPUT_LENGTH = 4_000;

	private static final Set<String> SUPPORTED_PLATFORMS = Set.of("fixture-social");
	private static final Set<String> SUPPORTED_MARKETS = Set.of("NASDAQ");
	private static final Set<String> FORBIDDEN_PHRASES = Set.of(
		"날씨", "코딩", "레시피", "매수", "매도", "투자 추천", "수익 예측", "가격 예측",
		"weather", "write code", "recipe", "buy recommendation", "sell recommendation",
		"price prediction", "return prediction", "ignore previous", "이전 지시", "system prompt",
		"시스템 프롬프트", "bash", "shell command"
	);

	public AnalysisScopeDecision evaluate(String userInput, AnalysisIntent intent) {
		if (userInput == null || userInput.isBlank()) {
			return AnalysisScopeDecision.incomplete("분석 요청을 입력해 주세요.");
		}
		if (userInput.length() > MAX_INPUT_LENGTH) {
			return AnalysisScopeDecision.rejected("요청이 허용된 길이를 초과했습니다.");
		}

		var normalizedInput = userInput.toLowerCase(Locale.ROOT);
		if (FORBIDDEN_PHRASES.stream().anyMatch(normalizedInput::contains)) {
			return AnalysisScopeDecision.rejected(domainRefusal());
		}
		if (intent == null || intent.taskType() != AnalysisIntent.TaskType.ANALYZE_INFLUENCER_POSTS) {
			return AnalysisScopeDecision.rejected(domainRefusal());
		}
		if (isBlank(intent.platform()) || isBlank(intent.influencerId()) || isBlank(intent.startInclusive())
			|| isBlank(intent.endExclusive()) || isBlank(intent.timezone())) {
			return AnalysisScopeDecision.incomplete(
				"SNS 플랫폼, 인플루언서 식별자, 시작/종료 시각과 timezone이 필요합니다."
			);
		}

		var platform = intent.platform().trim().toLowerCase(Locale.ROOT);
		if (!SUPPORTED_PLATFORMS.contains(platform)) {
			return AnalysisScopeDecision.rejected("현재 fixture-social 플랫폼만 분석할 수 있습니다.");
		}
		var markets = intent.marketCodes().stream()
			.map(code -> code.toUpperCase(Locale.ROOT))
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		if (!SUPPORTED_MARKETS.containsAll(markets)) {
			return AnalysisScopeDecision.rejected("현재 NASDAQ fixture 시장만 분석할 수 있습니다.");
		}
		if (!containsIgnoreCase(userInput, intent.platform())
			|| !containsIgnoreCase(userInput, intent.influencerId())
			|| !containsDate(userInput, intent.startInclusive())
			|| !containsDate(userInput, intent.endExclusive())) {
			return AnalysisScopeDecision.incomplete("요청에 플랫폼, 인플루언서와 기간을 명시해 주세요.");
		}

		try {
			var period = new AnalysisPeriod(
				Instant.parse(intent.startInclusive()),
				Instant.parse(intent.endExclusive()),
				ZoneId.of(intent.timezone())
			);
			return AnalysisScopeDecision.accepted(
				new InfluencerAnalysisRequest(platform, intent.influencerId(), period, markets)
			);
		} catch (DateTimeException exception) {
			return AnalysisScopeDecision.incomplete("시작/종료 시각은 ISO-8601 instant, timezone은 IANA 형식이어야 합니다.");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private boolean containsIgnoreCase(String source, String expected) {
		return source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private boolean containsDate(String source, String instant) {
		var dateSeparator = instant.indexOf('T');
		var requiredText = dateSeparator > 0 ? instant.substring(0, dateSeparator) : instant;
		return source.contains(requiredText);
	}

	private String domainRefusal() {
		return "Pinbabel은 특정 기간의 주식 인플루언서 SNS 포스트 종목 평가 분석만 수행합니다.";
	}
}
