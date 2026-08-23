package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;

class InfluencerAnalysisDomainTest {

	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant END = Instant.parse("2026-01-03T00:00:00Z");

	@Test
	void periodIncludesStartAndExcludesEnd() {
		var period = new AnalysisPeriod(START, END, ZoneId.of("Asia/Seoul"));

		assertThat(period.contains(START)).isTrue();
		assertThat(period.contains(END.minusNanos(1))).isTrue();
		assertThat(period.contains(END)).isFalse();
	}

	@Test
	void periodRejectsReversedOrEmptyRange() {
		assertThatThrownBy(() -> new AnalysisPeriod(END, START, ZoneId.of("UTC")))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.INVALID_PERIOD_ORDER));
		assertThatThrownBy(() -> new AnalysisPeriod(START, START, ZoneId.of("UTC")))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.INVALID_PERIOD_ORDER));
	}

	@Test
	void requestRejectsBlankPlatformAndInfluencer() {
		var period = new AnalysisPeriod(START, END, ZoneId.of("UTC"));

		assertThatThrownBy(() -> new InfluencerAnalysisRequest(" ", "market-voice", period, Set.of()))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.PLATFORM_REQUIRED));
		assertThatThrownBy(() -> new InfluencerAnalysisRequest("fixture-social", " ", period, Set.of()))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.INFLUENCER_REQUIRED));
	}

	@Test
	void requestNormalizesMarketCodesWithoutChangingExternalIdentifiers() {
		var period = new AnalysisPeriod(START, END, ZoneId.of("UTC"));

		var request = new InfluencerAnalysisRequest(
			"fixture-social",
			"0007-market-voice",
			period,
			Set.of(" nasdaq ", "NYSE")
		);

		assertThat(request.influencerId()).isEqualTo("0007-market-voice");
		assertThat(request.marketCodes()).containsExactlyInAnyOrder("NASDAQ", "NYSE");
	}

	@Test
	void periodRejectsMissingRequiredValues() {
		assertCode(() -> new AnalysisPeriod(null, END, ZoneId.of("UTC")),
			InfluencerAnalysisInternalCode.PERIOD_START_REQUIRED);
		assertCode(() -> new AnalysisPeriod(START, null, ZoneId.of("UTC")),
			InfluencerAnalysisInternalCode.PERIOD_END_REQUIRED);
		assertCode(() -> new AnalysisPeriod(START, END, null),
			InfluencerAnalysisInternalCode.PERIOD_TIMEZONE_REQUIRED);
	}

	@Test
	void requestRejectsMissingPeriodAndMarketCollection() {
		var period = new AnalysisPeriod(START, END, ZoneId.of("UTC"));

		assertCode(() -> new InfluencerAnalysisRequest("fixture-social", "voice", null, Set.of()),
			InfluencerAnalysisInternalCode.PERIOD_REQUIRED);
		assertCode(() -> new InfluencerAnalysisRequest("fixture-social", "voice", period, null),
			InfluencerAnalysisInternalCode.MARKET_CODES_REQUIRED);
	}

	@Test
	void collectedPostRejectsMissingProvenanceAndContent() {
		assertCode(() -> post(null, "fixture-social", "voice", START, URI.create("https://social.example/1"),
			"text", "fixture", PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_ID_REQUIRED);
		assertCode(() -> post("1", null, "voice", START, URI.create("https://social.example/1"),
			"text", "fixture", PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_PLATFORM_REQUIRED);
		assertCode(() -> post("1", "fixture-social", null, START, URI.create("https://social.example/1"),
			"text", "fixture", PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_AUTHOR_REQUIRED);
		assertCode(() -> post("1", "fixture-social", "voice", null, URI.create("https://social.example/1"),
			"text", "fixture", PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_PUBLISHED_AT_REQUIRED);
		assertCode(() -> post("1", "fixture-social", "voice", START, null,
			"text", "fixture", PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_URL_REQUIRED);
		assertCode(() -> post("1", "fixture-social", "voice", START, URI.create("https://social.example/1"),
			null, "fixture", PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_TEXT_REQUIRED);
		assertCode(() -> post("1", "fixture-social", "voice", START, URI.create("https://social.example/1"),
			"text", null, PostKind.ORIGINAL), InfluencerAnalysisInternalCode.POST_SOURCE_REQUIRED);
		assertCode(() -> post("1", "fixture-social", "voice", START, URI.create("https://social.example/1"),
			"text", "fixture", null), InfluencerAnalysisInternalCode.POST_KIND_REQUIRED);
	}

	@Test
	void collectedPostsAreBoundedAndDefensivelyCopied() {
		var mutablePosts = new ArrayList<CollectedPost>();
		mutablePosts.add(validPost("1"));
		var collected = new CollectedPosts(mutablePosts);
		mutablePosts.clear();

		assertThat(collected.posts()).extracting(CollectedPost::postId).containsExactly("1");
		assertThatThrownBy(() -> collected.posts().add(validPost("2")))
			.isInstanceOf(UnsupportedOperationException.class);
		assertCode(() -> new CollectedPosts(null), InfluencerAnalysisInternalCode.POSTS_REQUIRED);
		assertCode(() -> new CollectedPosts(Collections.singletonList(null)),
			InfluencerAnalysisInternalCode.POST_ITEM_REQUIRED);
		assertCode(() -> new CollectedPosts(Collections.nCopies(CollectedPosts.MAX_POSTS_PER_RUN + 1, validPost("1"))),
			InfluencerAnalysisInternalCode.TOO_MANY_POSTS);
	}

	@Test
	void instrumentReferenceNormalizesAndDefensivelyCopiesAliases() {
		var aliases = new ArrayList<>(List.of("NVIDIA", "엔비디아"));
		var instrument = new InstrumentReference("NASDAQ:NVDA", " nvda ", " nasdaq ", "NVIDIA", aliases);
		aliases.clear();

		assertThat(instrument.ticker()).isEqualTo("NVDA");
		assertThat(instrument.exchange()).isEqualTo("NASDAQ");
		assertThat(instrument.aliases()).containsExactly("NVIDIA", "엔비디아");
		assertCode(() -> new InstrumentReference(null, "NVDA", "NASDAQ", "NVIDIA", List.of()),
			InfluencerAnalysisInternalCode.INSTRUMENT_ID_REQUIRED);
		assertCode(() -> new InstrumentReference("NASDAQ:NVDA", null, "NASDAQ", "NVIDIA", List.of()),
			InfluencerAnalysisInternalCode.TICKER_REQUIRED);
		assertCode(() -> new InstrumentReference("NASDAQ:NVDA", "NVDA", null, "NVIDIA", List.of()),
			InfluencerAnalysisInternalCode.EXCHANGE_REQUIRED);
		assertCode(() -> new InstrumentReference("NASDAQ:NVDA", "NVDA", "NASDAQ", null, List.of()),
			InfluencerAnalysisInternalCode.DISPLAY_NAME_REQUIRED);
		assertCode(() -> new InstrumentReference("NASDAQ:NVDA", "NVDA", "NASDAQ", "NVIDIA", null),
			InfluencerAnalysisInternalCode.ALIASES_REQUIRED);
		assertCode(() -> new InstrumentReference("NASDAQ:NVDA", "NVDA", "NASDAQ", "NVIDIA",
			Collections.singletonList(null)), InfluencerAnalysisInternalCode.ALIASES_REQUIRED);
	}

	private CollectedPost validPost(String postId) {
		return post(postId, "fixture-social", "voice", START, URI.create("https://social.example/" + postId),
			"text", "fixture", PostKind.ORIGINAL);
	}

	private CollectedPost post(
		String postId,
		String platform,
		String authorId,
		Instant publishedAt,
		URI url,
		String text,
		String source,
		PostKind kind
	) {
		return new CollectedPost(postId, platform, authorId, publishedAt, url, text, source, kind);
	}

	private void assertCode(Executable executable, InfluencerAnalysisInternalCode expectedCode) {
		assertThatThrownBy(executable::execute)
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode()).isEqualTo(expectedCode));
	}
}
