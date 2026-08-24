package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class XSocialPostSourceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@AfterEach
	void clearInterruptedStatus() {
		Thread.interrupted();
	}

	@Test
	void collectsAtMostTenRecentOriginalOrQuotePostsWithoutPagination() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, """
				{
				  "data":[
				    {"id":"101","text":"older original $AAPL","author_id":"42","created_at":"2026-01-01T10:00:00Z"},
				    {"id":"104","text":"reply $MSFT","author_id":"42","created_at":"2026-01-04T10:00:00Z",
				     "referenced_tweets":[{"type":"replied_to","id":"90"}]},
				    {"id":"103","text":"quoted commentary $GOOGL","author_id":"42","created_at":"2026-01-03T10:00:00Z",
				     "referenced_tweets":[{"type":"quoted","id":"80"}]},
				    {"id":"105","text":"repost $TSLA","author_id":"42","created_at":"2026-01-05T10:00:00Z",
				     "referenced_tweets":[{"type":"retweeted","id":"70"}]},
				    {"id":"102","text":"newer original $NVDA","author_id":"42","created_at":"2026-01-02T10:00:00Z"}
				  ],
				  "includes":{"tweets":[{"id":"80","text":"quoted context","author_id":"77"}]},
				  "meta":{"next_token":"must-not-be-followed"}
				}
				""")
		);

		var result = new XSocialPostSource(objectMapper, client)
			.findRecentOriginalPosts(new XAccountHandle("@Market_Voice"));

		assertThat(result.account().displayHandle()).isEqualTo("@market_voice");
		assertThat(result.xApiRequestCount()).isEqualTo(2);
		assertThat(result.posts().posts())
			.extracting(post -> post.postId())
			.containsExactly("103", "102", "101");
		assertThat(result.posts().posts())
			.extracting(post -> post.kind())
			.containsExactly(PostKind.QUOTE, PostKind.ORIGINAL, PostKind.ORIGINAL);
		assertThat(result.posts().warnings())
			.containsExactly(XSocialPostSource.RECENT_ORIGINAL_COVERAGE_WARNING);
		assertThat(client.requestUris()).hasSize(2);
		assertThat(client.requestUris().getFirst().toString())
			.isEqualTo("https://api.x.com/2/users/by/username/market_voice");
		assertThat(client.requestUris().getLast().getRawQuery())
			.contains("max_results=10", "exclude=replies,retweets")
			.contains("tweet.fields=author_id,created_at,referenced_tweets,note_tweet")
			.doesNotContain("pagination_token", "start_time", "end_time");
	}

	@Test
	void defensivelyCapsRecentOriginalPostsWhenProviderReturnsMoreThanRequested() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, timelinePage(1, 12, "ignored-next-page"))
		);

		var result = new XSocialPostSource(objectMapper, client)
			.findRecentOriginalPosts(new XAccountHandle("market_voice"));

		assertThat(result.posts().posts()).hasSize(10);
		assertThat(client.requestUris()).hasSize(2);
	}

	@Test
	void resolvesUsernameAndCollectsEveryPageDeduplicatedAndOrdered() {
		var client = new RecordingXApiClient(
			response(200, """
				{"data":{"id":"42","username":"market_voice"}}
				"""),
			response(200, """
				{
				  "data":[
				    {"id":"200","text":"$NVDA looks strong","author_id":"42","created_at":"2026-01-02T10:00:00Z",
				     "referenced_tweets":[{"type":"quoted","id":"100"}]}
				  ],
				  "meta":{"next_token":"page-2"}
				}
				"""),
			response(200, """
				{
				  "data":[
				    {"id":"100","text":"$AAPL remains durable","author_id":"42","created_at":"2026-01-01T10:00:00Z"},
				    {"id":"200","text":"duplicate","author_id":"42","created_at":"2026-01-02T10:00:00Z"},
				    {"id":"300","text":"reply","author_id":"42","created_at":"2026-01-02T11:00:00Z",
				     "referenced_tweets":[{"type":"replied_to","id":"250"}]}
				  ],
				  "meta":{}
				}
				"""
			)
		);
		var source = new XSocialPostSource(objectMapper, client);

		var result = source.findPosts(request("@market_voice"));

		assertThat(result.posts()).extracting(post -> post.postId())
			.containsExactly("100", "200", "300");
		assertThat(result.posts()).extracting(post -> post.kind())
			.containsExactly(PostKind.ORIGINAL, PostKind.QUOTE, PostKind.REPLY);
		assertThat(result.warnings()).containsExactly(
			XSocialPostSource.TIMELINE_COVERAGE_WARNING,
			XSocialPostSource.REFERENCED_POST_WARNING
		);
		assertThat(result.posts()).allSatisfy(post -> {
			assertThat(post.platform()).isEqualTo("x");
			assertThat(post.authorId()).isEqualTo("42");
			assertThat(post.source()).isEqualTo("x-api-v2");
			assertThat(post.url().getHost()).isEqualTo("x.com");
		});
		assertThat(client.requestUris()).hasSize(3);
		assertThat(client.requestUris().getFirst().toString())
			.isEqualTo("https://api.x.com/2/users/by/username/market_voice");
		assertThat(client.requestUris().get(1).getRawQuery())
			.contains("max_results=50")
			.contains("start_time=2026-01-01T00%3A00%3A00Z")
			.contains("end_time=2026-01-03T00%3A00%3A00Z")
			.contains("tweet.fields=author_id,created_at,referenced_tweets,note_tweet")
			.contains("expansions=referenced_tweets.id");
		assertThat(client.requestUris().get(2).getRawQuery())
			.contains("pagination_token=page-2");
	}

	@Test
	void mapsRetweetReferenceToRepost() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, """
				{"data":[{"id":"400","text":"RT","author_id":"42","created_at":"2026-01-01T10:00:00Z",
				"referenced_tweets":[{"type":"retweeted","id":"399"}]}],"meta":{}}
				""")
		);

		var result = new XSocialPostSource(objectMapper, client).findPosts(request("market_voice"));

		assertThat(result.posts()).singleElement().extracting(post -> post.kind()).isEqualTo(PostKind.REPOST);
	}

	@Test
	void usesLongFormTextAndExpandedRepostText() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, """
				{
				  "data":[
				    {"id":"401","text":"short","note_tweet":{"text":"full long-form $NVDA view"},
				     "author_id":"42","created_at":"2026-01-01T10:00:00Z"},
				    {"id":"402","text":"RT truncated","author_id":"42","created_at":"2026-01-01T11:00:00Z",
				     "referenced_tweets":[{"type":"retweeted","id":"399"}]}
				  ],
				  "includes":{"tweets":[
				    {"id":"399","text":"referenced short","note_tweet":{"text":"complete repost $AAPL view"}}
				  ]},
				  "meta":{}
				}
				""")
		);

		var result = new XSocialPostSource(objectMapper, client).findPosts(request("market_voice"));

		assertThat(result.posts()).extracting(post -> post.text())
			.containsExactly(
				"full long-form $NVDA view",
				"INFLUENCER_REPOST_WITHOUT_EXPLICIT_ENDORSEMENT:\nauthor_id=unknown\ncomplete repost $AAPL view"
			);
	}

	@Test
	void distinguishesAQuotedPriorInfluencerPostFromAnotherAuthorsSpeech() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, """
				{
				  "data":[{"id":"403","text":"still true","author_id":"42","created_at":"2026-01-01T12:00:00Z",
				    "referenced_tweets":[{"type":"quoted","id":"398"}]}],
				  "includes":{"tweets":[{"id":"398","text":"earlier $NVDA view","author_id":"42"}]},
				  "meta":{}
				}
				""")
		);

		var result = new XSocialPostSource(objectMapper, client).findPosts(request("market_voice"));

		assertThat(result.posts()).singleElement().satisfies(post ->
			assertThat(post.text())
				.contains("INFLUENCER_COMMENTARY", "QUOTED_PRIOR_INFLUENCER_POST_CONTEXT", "$NVDA")
				.doesNotContain("NOT_INFLUENCER_SPEECH")
		);
	}

	@Test
	void reportsPartialProviderResponsesWithoutLeakingProviderDetails() {
		var client = new RecordingXApiClient(
			response(200, """
				{"data":{"id":"42","username":"market_voice"},
				 "errors":[{"title":"hidden lookup detail"}]}
				"""),
			response(200, """
				{"data":[],"meta":{},"errors":[{"detail":"hidden timeline detail"}]}
				""")
		);

		var result = new XSocialPostSource(objectMapper, client).findPosts(request("market_voice"));

		assertThat(result.warnings()).containsExactly(
			XSocialPostSource.PARTIAL_RESPONSE_WARNING,
			XSocialPostSource.TIMELINE_COVERAGE_WARNING
		);
		assertThat(result.warnings()).noneMatch(warning -> warning.contains("hidden"));
	}

	@Test
	void enforcesStartInclusiveAndEndExclusiveBoundaries() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, """
				{"data":[
				 {"id":"1","text":"before","author_id":"42","created_at":"2025-12-31T23:59:59Z"},
				 {"id":"2","text":"start","author_id":"42","created_at":"2026-01-01T00:00:00Z"},
				 {"id":"3","text":"inside","author_id":"42","created_at":"2026-01-02T23:59:59Z"},
				 {"id":"4","text":"end","author_id":"42","created_at":"2026-01-03T00:00:00Z"}
				],"meta":{}}
				""")
		);

		var result = new XSocialPostSource(objectMapper, client).findPosts(request("market_voice"));

		assertThat(result.posts()).extracting(post -> post.postId()).containsExactly("2", "3");
	}

	@Test
	void rejectsInvalidUsernameBeforeCallingX() {
		var client = new RecordingXApiClient();
		var source = new XSocialPostSource(objectMapper, client);

		assertCode(
			() -> source.findPosts(request("invalid/name")),
			InfluencerAnalysisInternalCode.X_USERNAME_INVALID
		);
		assertThat(client.requestUris()).isEmpty();
	}

	@Test
	void requiresBearerTokenWhenTheXProfileAdapterIsCreated() {
		assertCode(
			() -> new XSocialPostSource(objectMapper, new MockEnvironment()),
			InfluencerAnalysisInternalCode.X_BEARER_TOKEN_REQUIRED
		);
	}

	@Test
	void translatesRejectedAndMalformedResponsesWithoutLeakingTheirBodies() {
		var rejected = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(418, "{\"detail\":\"secret provider detail\"}")
		));
		var malformed = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(200, "not-json")
		));

		assertThatThrownBy(() -> rejected.findPosts(request("market_voice")))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception -> {
				assertThat(exception.internalCode()).isEqualTo(InfluencerAnalysisInternalCode.X_API_RESPONSE_REJECTED);
				assertThat(exception.getMessage()).doesNotContain("secret provider detail");
			});
		assertCode(
			() -> malformed.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_API_RESPONSE_INVALID
		);
	}

	@Test
	void identifiesMissingPayPerUseCredits() {
		var source = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(402, "{\"detail\":\"payment required\"}")
		));

		assertCode(
			() -> source.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_API_CREDITS_REQUIRED
		);
	}

	@Test
	void identifiesRateLimitWithoutLeakingTheProviderBody() {
		var source = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(429, "{\"detail\":\"secret rate limit detail\"}")
		));

		assertThatThrownBy(() -> source.findPosts(request("market_voice")))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception -> {
				assertThat(exception.internalCode()).isEqualTo(InfluencerAnalysisInternalCode.X_API_RATE_LIMITED);
				assertThat(exception.getMessage()).doesNotContain("secret rate limit detail");
			});
	}

	@Test
	void classifiesServiceOutageAndAccessDeniedResponses() {
		var outage = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(503, "{\"detail\":\"provider outage detail\"}")
		));
		var denied = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(403, "{\"detail\":\"provider access detail\"}")
		));

		assertCode(
			() -> outage.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_API_SERVICE_UNAVAILABLE
		);
		assertCode(
			() -> denied.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_API_ACCESS_DENIED
		);
	}

	@Test
	void rejectsUnexpectedContentType() {
		var source = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			new XApiResponse(200, "text/html", "<html>error</html>".getBytes(StandardCharsets.UTF_8))
		));

		assertCode(
			() -> source.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_API_CONTENT_TYPE_INVALID
		);
	}

	@Test
	void rejectsProviderJsonBeyondTheConfiguredNestingDepth() {
		var deeplyNestedValue = "[".repeat(40) + "0" + "]".repeat(40);
		var source = new XSocialPostSource(objectMapper, new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"},\"extra\":"
				+ deeplyNestedValue + "}")
		));

		assertCode(
			() -> source.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_API_RESPONSE_INVALID
		);
	}

	@Test
	void translatesLookupIoFailureAndPreservesInterruptStatus() {
		var ioFailure = new XSocialPostSource(objectMapper, uri -> {
			throw new IOException("network down");
		});
		var interrupted = new XSocialPostSource(objectMapper, uri -> {
			throw new InterruptedException("cancelled");
		});

		assertCode(
			() -> ioFailure.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_USER_LOOKUP_REQUEST_FAILED
		);
		assertCode(
			() -> interrupted.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_USER_LOOKUP_INTERRUPTED
		);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
	}

	@Test
	void translatesTimelineIoFailureAtTheOutboundBoundary() {
		var calls = new AtomicInteger();
		var source = new XSocialPostSource(objectMapper, uri -> {
			if (calls.getAndIncrement() == 0) {
				return response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}");
			}
			throw new IOException("timeline unavailable");
		});

		assertCode(
			() -> source.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_TIMELINE_REQUEST_FAILED
		);
	}

	@Test
	void translatesTimelineInterruptionAndPreservesInterruptStatus() {
		var calls = new AtomicInteger();
		var source = new XSocialPostSource(objectMapper, uri -> {
			if (calls.getAndIncrement() == 0) {
				return response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}");
			}
			throw new InterruptedException("timeline cancelled");
		});

		assertCode(
			() -> source.findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_TIMELINE_REQUEST_INTERRUPTED
		);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
	}

	@Test
	void failsInsteadOfReturningAHiddenPartialResultWhenPostLimitIsExceeded() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, timelinePage(1, CollectedPosts.MAX_POSTS_PER_RUN, "more")),
			response(200, timelinePage(51, 1, null))
		);

		assertCode(
			() -> new XSocialPostSource(objectMapper, client).findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_POST_LIMIT_EXCEEDED
		);
		assertThat(client.requestUris()).hasSize(2);
	}

	@Test
	void rejectsRepeatedPaginationToken() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, "{\"data\":[],\"meta\":{\"next_token\":\"same\"}}"),
			response(200, "{\"data\":[],\"meta\":{\"next_token\":\"same\"}}")
		);

		assertCode(
			() -> new XSocialPostSource(objectMapper, client).findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_PAGINATION_CYCLE
		);
	}

	@Test
	void rejectsUniquePaginationTokensBeyondThePageCeiling() {
		var responses = new ArrayList<XApiResponse>();
		responses.add(response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"));
		for (var page = 1; page <= XSocialPostSource.MAX_PAGES; page++) {
			responses.add(response(200, "{\"data\":[],\"meta\":{\"next_token\":\"page-" + page + "\"}}"));
		}
		var client = new RecordingXApiClient(responses.toArray(XApiResponse[]::new));

		assertCode(
			() -> new XSocialPostSource(objectMapper, client).findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_PAGINATION_LIMIT_EXCEEDED
		);
		assertThat(client.requestUris()).hasSize(XSocialPostSource.MAX_PAGES + 1);
	}

	@Test
	void rejectsMalformedTimelinePostInsteadOfReturningPartialData() {
		var client = new RecordingXApiClient(
			response(200, "{\"data\":{\"id\":\"42\",\"username\":\"market_voice\"}}"),
			response(200, """
				{"data":[{"id":"not-numeric","text":"bad","author_id":"42",
				 "created_at":"2026-01-01T10:00:00Z"}],"meta":{}}
				""")
		);

		assertCode(
			() -> new XSocialPostSource(objectMapper, client).findPosts(request("market_voice")),
			InfluencerAnalysisInternalCode.X_POST_MAPPING_FAILED
		);
	}

	@Test
	void returnsEmptyWithoutCallingXForAnotherPlatform() {
		var client = new RecordingXApiClient();
		var xRequest = request("market_voice");
		var fixtureRequest = new InfluencerAnalysisRequest(
			"fixture-social", xRequest.influencerId(), xRequest.period(), xRequest.marketCodes()
		);

		var result = new XSocialPostSource(objectMapper, client).findPosts(fixtureRequest);

		assertThat(result.isEmpty()).isTrue();
		assertThat(client.requestUris()).isEmpty();
	}

	private InfluencerAnalysisRequest request(String influencerId) {
		return new InfluencerAnalysisRequest(
			"x",
			influencerId,
			new AnalysisPeriod(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-03T00:00:00Z"),
				ZoneId.of("UTC")
			),
			Set.of("NASDAQ")
		);
	}

	private XApiResponse response(int status, String body) {
		return new XApiResponse(status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
	}

	private String timelinePage(int firstId, int count, String nextToken) {
		var posts = new ArrayList<String>();
		for (var index = 0; index < count; index++) {
			var id = firstId + index;
			posts.add("""
				{"id":"%d","text":"post %d","author_id":"42","created_at":"2026-01-01T10:00:00Z"}
				""".formatted(id, id).trim());
		}
		var meta = nextToken == null ? "{}" : "{\"next_token\":\"" + nextToken + "\"}";
		return "{\"data\":[" + String.join(",", posts) + "],\"meta\":" + meta + "}";
	}

	private void assertCode(Runnable invocation, InfluencerAnalysisInternalCode code) {
		assertThatThrownBy(invocation::run)
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode()).isEqualTo(code));
	}

	private static final class RecordingXApiClient implements XApiClient {

		private final ArrayDeque<XApiResponse> responses = new ArrayDeque<>();
		private final List<URI> requestUris = new ArrayList<>();

		private RecordingXApiClient(XApiResponse... responses) {
			this.responses.addAll(List.of(responses));
		}

		@Override
		public XApiResponse get(URI uri) {
			requestUris.add(uri);
			return responses.removeFirst();
		}

		private List<URI> requestUris() {
			return List.copyOf(requestUris);
		}
	}
}
