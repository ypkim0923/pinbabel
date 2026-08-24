package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentXPostBatch;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.RecentSocialPostSource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("x")
@SecondaryAdapter
public class XSocialPostSource implements SocialPostSource, RecentSocialPostSource {

	static final URI API_BASE_URI = URI.create("https://api.x.com");
	static final int PAGE_SIZE = CollectedPosts.MAX_POSTS_PER_RUN;
	static final int MAX_PAGES = 32;
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
	static final String TIMELINE_COVERAGE_WARNING =
		"X_TIMELINE_LIMITED_TO_3200_MOST_RECENT_POSTS";
	static final String PARTIAL_RESPONSE_WARNING = "X_API_PARTIAL_RESPONSE";
	static final String REFERENCED_POST_WARNING = "X_REFERENCED_POST_UNAVAILABLE";
	static final String RECENT_ORIGINAL_COVERAGE_WARNING =
		"X_RECENT_ORIGINAL_POSTS_LIMITED_TO_800_MOST_RECENT_POSTS";

	private static final Pattern X_ID = Pattern.compile("[0-9]{1,19}");
	private static final Comparator<CollectedPost> POST_ORDER = Comparator
		.comparing(CollectedPost::publishedAt)
		.thenComparing(CollectedPost::postId);
	private static final JsonFactory X_JSON_FACTORY = JsonFactory.builder()
		.streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).build())
		.build();

	private final ObjectMapper objectMapper;
	private final XApiClient apiClient;

	@Autowired
	public XSocialPostSource(ObjectMapper objectMapper, Environment environment) {
		this(objectMapper, clientFrom(environment));
	}

	XSocialPostSource(ObjectMapper objectMapper, XApiClient apiClient) {
		this.objectMapper = objectMapper;
		this.apiClient = apiClient;
	}

	@Override
	public CollectedPosts findPosts(InfluencerAnalysisRequest request) {
		if (!"x".equals(request.platform())) {
			return new CollectedPosts(List.of());
		}
		var username = normalizedUsername(request.influencerId());
		var lookup = lookupUser(username);
		return collectTimeline(lookup.user(), username, request, lookup.warnings());
	}

	@Override
	public Set<String> supportedPlatforms() {
		return Set.of("x");
	}

	@Override
	public RecentXPostBatch findRecentOriginalPosts(XAccountHandle account) {
		var lookup = lookupUser(account.username());
		var response = requestRecentOriginalTimeline(lookup.user().id());
		var payload = readResponse(response, TimelineResponse.class);
		var warnings = new ArrayList<>(lookup.warnings());
		warnings.addAll(partialResponseWarnings(payload.errors()));
		warnings.add(RECENT_ORIGINAL_COVERAGE_WARNING);
		var includedPostsById = payload.includedPostsById();
		var postsById = new LinkedHashMap<String, CollectedPost>();
		for (var post : payload.dataOrEmpty()) {
			if (post.kind() == PostKind.REPLY || post.kind() == PostKind.REPOST) {
				continue;
			}
			if (post.hasUnavailableAnalysisReference(includedPostsById)) {
				warnings.add(REFERENCED_POST_WARNING);
			}
			var collectedPost = toDomain(post, includedPostsById, account.username());
			postsById.putIfAbsent(collectedPost.postId(), collectedPost);
		}
		var posts = postsById.values().stream()
			.sorted(POST_ORDER.reversed())
			.limit(RECENT_POST_LIMIT)
			.toList();
		return new RecentXPostBatch(
			account,
			new CollectedPosts(posts, warnings.stream().distinct().toList()),
			2
		);
	}

	private UserLookupResult lookupUser(String username) {
		XApiResponse response;
		try {
			response = apiClient.get(API_BASE_URI.resolve("/2/users/by/username/" + username));
		} catch (IOException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_USER_LOOKUP_REQUEST_FAILED,
				"Unable to query the X user lookup API",
				exception
			);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_USER_LOOKUP_INTERRUPTED,
				"X user lookup was interrupted",
				exception
			);
		}
		var payload = readResponse(response, UserLookupResponse.class);
		if (payload.data() == null || payload.data().id() == null
			|| !X_ID.matcher(payload.data().id()).matches()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_USER_NOT_FOUND,
				"The requested public X user was not found"
			);
		}
		return new UserLookupResult(payload.data(), partialResponseWarnings(payload.errors()));
	}

	private CollectedPosts collectTimeline(
		XUser user,
		String username,
		InfluencerAnalysisRequest request,
		List<String> lookupWarnings
	) {
		var postsById = new LinkedHashMap<String, CollectedPost>();
		var warnings = new ArrayList<>(lookupWarnings);
		warnings.add(TIMELINE_COVERAGE_WARNING);
		var seenTokens = new HashSet<String>();
		String nextToken = null;
		for (var page = 0; page < MAX_PAGES; page++) {
			var response = requestTimeline(user.id(), request, nextToken);
			var payload = readResponse(response, TimelineResponse.class);
			warnings.addAll(partialResponseWarnings(payload.errors()));
			var includedPostsById = payload.includedPostsById();
			for (var post : payload.dataOrEmpty()) {
				if (post.hasUnavailableAnalysisReference(includedPostsById)) {
					warnings.add(REFERENCED_POST_WARNING);
				}
				var collectedPost = toDomain(post, includedPostsById, username);
				if (request.period().contains(collectedPost.publishedAt())) {
					postsById.putIfAbsent(collectedPost.postId(), collectedPost);
				}
				if (postsById.size() > CollectedPosts.MAX_POSTS_PER_RUN) {
					throw postLimitExceeded("X posts exceed the per-run analysis limit");
				}
			}
			nextToken = payload.nextToken();
			if (nextToken == null || nextToken.isBlank()) {
				return ordered(postsById, warnings);
			}
			if (postsById.size() == CollectedPosts.MAX_POSTS_PER_RUN) {
				throw postLimitExceeded("X posts reach the per-run analysis limit and more pages remain");
			}
			if (!seenTokens.add(nextToken)) {
				throw new InfluencerAnalysisException(
					InfluencerAnalysisInternalCode.X_PAGINATION_CYCLE,
					"X API returned a repeated pagination token"
				);
			}
		}
		throw new InfluencerAnalysisException(
			InfluencerAnalysisInternalCode.X_PAGINATION_LIMIT_EXCEEDED,
			"X API pagination exceeded the configured page limit"
		);
	}

	private XApiResponse requestTimeline(
		String userId,
		InfluencerAnalysisRequest request,
		String paginationToken
	) {
		var period = request.period();
		var query = new StringBuilder()
			.append("max_results=").append(PAGE_SIZE)
			.append("&start_time=").append(encode(period.startInclusive().toString()))
			.append("&end_time=").append(encode(period.endExclusive().toString()))
			.append("&tweet.fields=author_id,created_at,referenced_tweets,note_tweet")
			.append("&expansions=referenced_tweets.id");
		if (paginationToken != null) {
			query.append("&pagination_token=").append(encode(paginationToken));
		}
		var uri = API_BASE_URI.resolve("/2/users/" + userId + "/tweets?" + query);
		return requestTimeline(uri);
	}

	private XApiResponse requestTimeline(URI uri) {
		try {
			return apiClient.get(uri);
		} catch (IOException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_TIMELINE_REQUEST_FAILED,
				"Unable to query the X user posts API",
				exception
			);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_TIMELINE_REQUEST_INTERRUPTED,
				"X user posts request was interrupted",
				exception
			);
		}
	}

	private XApiResponse requestRecentOriginalTimeline(String userId) {
		var query = "max_results=" + RECENT_POST_LIMIT
			+ "&exclude=replies,retweets"
			+ "&tweet.fields=author_id,created_at,referenced_tweets,note_tweet"
			+ "&expansions=referenced_tweets.id";
		var uri = API_BASE_URI.resolve("/2/users/" + userId + "/tweets?" + query);
		return requestTimeline(uri);
	}

	private <T> T readResponse(XApiResponse response, Class<T> responseType) {
		if (response.statusCode() == 402) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_CREDITS_REQUIRED,
				"X API credits are required for this request"
			);
		}
		if (response.statusCode() == 429) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_RATE_LIMITED,
				"X API rate limit was exceeded"
			);
		}
		if (response.statusCode() >= 500 && response.statusCode() <= 599) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_SERVICE_UNAVAILABLE,
				"X API service is temporarily unavailable"
			);
		}
		if (response.statusCode() == 401 || response.statusCode() == 403) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_ACCESS_DENIED,
				"X API denied access to this request"
			);
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_RESPONSE_REJECTED,
				"X API rejected the request with status " + response.statusCode()
			);
		}
		if (response.contentType() == null
			|| !response.contentType().toLowerCase(Locale.ROOT).startsWith("application/json")) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_CONTENT_TYPE_INVALID,
				"X API returned an unsupported content type"
			);
		}
		try {
			try (var parser = X_JSON_FACTORY.createParser(ObjectReadContext.empty(), response.body())) {
				return objectMapper.readValue(parser, responseType);
			}
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_API_RESPONSE_INVALID,
				"X API returned an invalid response",
				exception
			);
		}
	}

	private CollectedPost toDomain(XPost post, Map<String, XPost> includedPostsById, String username) {
		try {
			if (post.id() == null || !X_ID.matcher(post.id()).matches()
				|| post.authorId() == null || !X_ID.matcher(post.authorId()).matches()) {
				throw new IllegalArgumentException("X identifiers must be numeric");
			}
			return new CollectedPost(
				post.id(),
				"x",
				post.authorId(),
				Instant.parse(post.createdAt()),
				URI.create("https://x.com/" + username + "/status/" + post.id()),
				post.analysisText(includedPostsById, post.authorId()),
				"x-api-v2",
				post.kind()
			);
		} catch (DateTimeParseException | IllegalArgumentException | InfluencerAnalysisException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_POST_MAPPING_FAILED,
				"X API post could not be mapped to the analysis model",
				exception
			);
		}
	}

	private CollectedPosts ordered(
		LinkedHashMap<String, CollectedPost> postsById,
		List<String> warnings
	) {
		return new CollectedPosts(
			postsById.values().stream().sorted(POST_ORDER).toList(),
			warnings.stream().distinct().toList()
		);
	}

	private List<String> partialResponseWarnings(List<XApiError> errors) {
		return errors == null || errors.isEmpty() ? List.of() : List.of(PARTIAL_RESPONSE_WARNING);
	}

	private InfluencerAnalysisException postLimitExceeded(String reason) {
		return new InfluencerAnalysisException(
			InfluencerAnalysisInternalCode.X_POST_LIMIT_EXCEEDED,
			reason + "; narrow the requested period"
		);
	}

	private static XApiClient clientFrom(Environment environment) {
		var bearerToken = environment.getProperty("X_BEARER_TOKEN");
		if (bearerToken == null || bearerToken.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_BEARER_TOKEN_REQUIRED,
				"X_BEARER_TOKEN is required for the x profile"
			);
		}
		return new JdkXApiClient(bearerToken, CONNECT_TIMEOUT, REQUEST_TIMEOUT);
	}

	private static String normalizedUsername(String influencerId) {
		return new XAccountHandle(influencerId).username();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private record UserLookupResponse(XUser data, List<XApiError> errors) {
	}

	private record UserLookupResult(XUser user, List<String> warnings) {
	}

	private record XUser(String id, String username) {
	}

	private record TimelineResponse(
		List<XPost> data,
		TimelineMeta meta,
		XIncludes includes,
		List<XApiError> errors
	) {

		private List<XPost> dataOrEmpty() {
			return data == null ? List.of() : data;
		}

		private String nextToken() {
			return meta == null ? null : meta.nextToken();
		}

		private Map<String, XPost> includedPostsById() {
			if (includes == null || includes.tweets() == null) {
				return Map.of();
			}
			var posts = new LinkedHashMap<String, XPost>();
			includes.tweets().stream()
				.filter(post -> post != null && post.id() != null)
				.forEach(post -> posts.putIfAbsent(post.id(), post));
			return Map.copyOf(posts);
		}
	}

	private record XIncludes(List<XPost> tweets) {
	}

	private record TimelineMeta(@JsonProperty("next_token") String nextToken) {
	}

	private record XPost(
		String id,
		String text,
		@JsonProperty("note_tweet") NoteTweet noteTweet,
		@JsonProperty("author_id") String authorId,
		@JsonProperty("created_at") String createdAt,
		@JsonProperty("referenced_tweets") List<ReferencedPost> referencedPosts
	) {

		private String analysisText(Map<String, XPost> includedPostsById, String influencerUserId) {
			if (kind() == PostKind.REPOST) {
				var repostedId = referencedId("retweeted");
				var reposted = includedPostsById.get(repostedId);
				if (reposted != null) {
					var label = influencerUserId.equals(reposted.authorId())
						? "INFLUENCER_REPOST_OF_PRIOR_OWN_POST_WITHOUT_NEW_COMMENTARY:\n"
						: "INFLUENCER_REPOST_WITHOUT_EXPLICIT_ENDORSEMENT:\n";
					return label
						+ referencedText(reposted);
				}
			}
			if (kind() == PostKind.QUOTE) {
				var quoted = includedPostsById.get(referencedId("quoted"));
				if (quoted != null) {
					var label = influencerUserId.equals(quoted.authorId())
						? "QUOTED_PRIOR_INFLUENCER_POST_CONTEXT:\n"
						: "QUOTED_POST_CONTEXT_NOT_INFLUENCER_SPEECH:\n";
					return "INFLUENCER_COMMENTARY:\n" + fullText()
						+ "\n\n" + label + referencedText(quoted);
				}
			}
			return fullText();
		}

		private boolean hasUnavailableAnalysisReference(Map<String, XPost> includedPostsById) {
			var referenceId = switch (kind()) {
				case REPOST -> referencedId("retweeted");
				case QUOTE -> referencedId("quoted");
				default -> null;
			};
			return referenceId != null && !includedPostsById.containsKey(referenceId);
		}

		private String referencedText(XPost referencedPost) {
			var author = referencedPost.authorId() == null ? "unknown" : referencedPost.authorId();
			return "author_id=" + author + "\n" + referencedPost.fullText();
		}

		private String fullText() {
			return noteTweet != null && noteTweet.text() != null && !noteTweet.text().isBlank()
				? noteTweet.text()
				: text;
		}

		private String referencedId(String expectedType) {
			if (referencedPosts == null) {
				return null;
			}
			return referencedPosts.stream()
				.filter(reference -> expectedType.equalsIgnoreCase(reference.type()))
				.map(ReferencedPost::id)
				.findFirst()
				.orElse(null);
		}

		private PostKind kind() {
			var types = referencedPosts == null
				? Set.<String>of()
				: referencedPosts.stream()
					.map(ReferencedPost::type)
					.filter(type -> type != null)
					.map(type -> type.toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableSet());
			if (types.contains("replied_to")) {
				return PostKind.REPLY;
			}
			if (types.contains("quoted")) {
				return PostKind.QUOTE;
			}
			if (types.contains("retweeted")) {
				return PostKind.REPOST;
			}
			return PostKind.ORIGINAL;
		}
	}

	private record NoteTweet(String text) {
	}

	private record ReferencedPost(String type, String id) {
	}

	private record XApiError(String title, String detail, Integer status) {
	}
}
