package com.ypkim.pinbabel.influenceranalysis.application.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.tool.Tool;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.InstrumentCatalog;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentReference;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnalysisWorkspaceToolsTest {

	@Test
	void discoversExactlyFourNamedEmbabelToolsWithBoundedSchemas() {
		var workspace = workspace(List.of(post("post-1", "content")), List.of(instrument("NASDAQ", "NVDA")));

		var tools = Tool.fromInstance(workspace, new ObjectMapper());
		var toolsByName = tools.stream().collect(Collectors.toMap(
			tool -> tool.getDefinition().getName(),
			tool -> tool
		));

		assertThat(tools)
			.extracting(tool -> tool.getDefinition().getName())
			.containsExactlyInAnyOrder("list_posts", "read_post", "search_instruments", "read_instrument");
		assertThat(tools).allSatisfy(tool -> {
			assertThat(tool.getDefinition().getDescription()).isNotBlank();
			assertThat(tool.getDefinition().getInputSchema().toJsonSchema()).doesNotContain("path", "url", "command");
		});
		assertThat(parameters(toolsByName, "list_posts")).isEmpty();
		assertThat(parameters(toolsByName, "read_post"))
			.extracting(Tool.Parameter::getName, Tool.Parameter::getRequired)
			.containsExactly(tuple("postId", true));
		assertThat(parameters(toolsByName, "search_instruments"))
			.extracting(Tool.Parameter::getName, Tool.Parameter::getRequired)
			.containsExactly(tuple("query", true));
		assertThat(parameters(toolsByName, "read_instrument"))
			.extracting(Tool.Parameter::getName, Tool.Parameter::getRequired)
			.containsExactly(tuple("instrumentId", true));
		assertThat(AnalysisWorkspaceTools.class.getDeclaredMethods())
			.filteredOn(method -> method.isAnnotationPresent(LlmTool.class))
			.hasSize(4)
			.allSatisfy(method -> assertThat(method.getReturnType()).isNotEqualTo(Void.TYPE));
	}

	@Test
	void listPostsReturnsStableMetadataOnlyAndReportsTruncation() {
		var posts = IntStream.range(0, 60)
			.mapToObj(index -> post("post-%02d".formatted(index), "private-content-" + index))
			.toList();
		var workspace = workspace(posts, List.of());

		var result = workspace.listPosts();

		assertThat(result.status()).isEqualTo(AnalysisWorkspaceTools.Status.OK);
		assertThat(result.totalCount()).isEqualTo(60);
		assertThat(result.items()).hasSize(AnalysisWorkspaceTools.MAX_LIST_ITEMS);
		assertThat(result.truncated()).isTrue();
		assertThat(result.items()).extracting(AnalysisWorkspaceTools.PostSummary::postId)
			.containsExactlyElementsOf(posts.stream().limit(AnalysisWorkspaceTools.MAX_LIST_ITEMS)
				.map(CollectedPost::postId).toList());
		assertThat(result.toString()).doesNotContain("private-content");
	}

	@Test
	void readPostReturnsProvenanceAndExplicitlyMarksContentTruncation() {
		var originalText = "x".repeat(AnalysisWorkspaceTools.MAX_POST_TEXT_LENGTH + 37);
		var workspace = workspace(List.of(post("post-1", originalText)), List.of());

		var result = workspace.readPost("  post-1  ");

		assertThat(result.status()).isEqualTo(AnalysisWorkspaceTools.Status.OK);
		assertThat(result.post()).isNotNull();
		assertThat(result.post().text()).hasSize(AnalysisWorkspaceTools.MAX_POST_TEXT_LENGTH);
		assertThat(result.post().originalTextLength()).isEqualTo(originalText.length());
		assertThat(result.post().textTruncated()).isTrue();
		assertThat(result.message()).contains("truncated", "10000");
		assertThat(result.post().url()).isEqualTo("https://social.example/posts/post-1");
		assertThat(result.post().source()).isEqualTo("pinbabel-fixture");
	}

	@Test
	void postLookupRejectsInvalidIdsAndCannotReadOutsideWorkspace() {
		var workspace = workspace(List.of(post("post-1", "content")), List.of());

		assertThat(workspace.readPost(" ").status()).isEqualTo(AnalysisWorkspaceTools.Status.INVALID_INPUT);
		assertThat(workspace.readPost("x".repeat(AnalysisWorkspaceTools.MAX_IDENTIFIER_LENGTH + 1)).status())
			.isEqualTo(AnalysisWorkspaceTools.Status.INVALID_INPUT);
		assertThat(workspace.readPost("post-outside").status()).isEqualTo(AnalysisWorkspaceTools.Status.NOT_FOUND);
	}

	@Test
	void searchesInstrumentsWithNormalizedBoundedQueryAndWorkspaceMarketScope() {
		var catalog = new RecordingInstrumentCatalog(List.of(
			instrument("NASDAQ", "NVDA"),
			instrument("NYSE", "IBM")
		));
		var workspace = new AnalysisWorkspaceTools(new CollectedPosts(List.of()), Set.of(" nasdaq "), catalog);

		var result = workspace.searchInstruments("  NvDa  ");

		assertThat(result.status()).isEqualTo(AnalysisWorkspaceTools.Status.OK);
		assertThat(result.items()).extracting(AnalysisWorkspaceTools.InstrumentItem::instrumentId)
			.containsExactly("NASDAQ:NVDA");
		assertThat(catalog.lastQuery).isEqualTo("nvda");
		assertThat(catalog.lastMarkets).containsExactly("NASDAQ");
		assertThat(catalog.lastLimit).isEqualTo(AnalysisWorkspaceTools.MAX_LIST_ITEMS);
	}

	@Test
	void instrumentToolsReturnBoundedErrorsAndDoNotLeakOtherMarkets() {
		var catalog = new RecordingInstrumentCatalog(List.of(
			instrument("NASDAQ", "NVDA"),
			instrument("NYSE", "IBM")
		));
		var workspace = new AnalysisWorkspaceTools(new CollectedPosts(List.of()), Set.of("NASDAQ"), catalog);

		assertThat(workspace.searchInstruments("").status()).isEqualTo(AnalysisWorkspaceTools.Status.INVALID_INPUT);
		assertThat(workspace.searchInstruments("x".repeat(AnalysisWorkspaceTools.MAX_QUERY_LENGTH + 1)).status())
			.isEqualTo(AnalysisWorkspaceTools.Status.INVALID_INPUT);
		assertThat(workspace.readInstrument("NYSE:IBM").status()).isEqualTo(AnalysisWorkspaceTools.Status.NOT_FOUND);
		assertThat(workspace.readInstrument("missing").status()).isEqualTo(AnalysisWorkspaceTools.Status.NOT_FOUND);
		assertThat(workspace.readInstrument(null).status()).isEqualTo(AnalysisWorkspaceTools.Status.INVALID_INPUT);
	}

	@Test
	void instrumentSearchEnforcesResultLimitEvenWhenCatalogReturnsTooManyItems() {
		var instruments = IntStream.range(0, 60)
			.mapToObj(index -> instrument("NASDAQ", "T%02d".formatted(index)))
			.toList();
		var workspace = workspace(List.of(), instruments);

		var result = workspace.searchInstruments("ticker");

		assertThat(result.status()).isEqualTo(AnalysisWorkspaceTools.Status.OK);
		assertThat(result.totalCount()).isEqualTo(60);
		assertThat(result.items()).hasSize(AnalysisWorkspaceTools.MAX_LIST_ITEMS);
		assertThat(result.truncated()).isTrue();
	}

	@Test
	void embabelInvokesReadToolAndSerializesStructuredResult() {
		var workspace = workspace(List.of(post("post-1", "untrusted content")), List.of());
		var readPost = Tool.fromInstance(workspace, new ObjectMapper()).stream()
			.filter(tool -> tool.getDefinition().getName().equals("read_post"))
			.findFirst()
			.orElseThrow();

		var result = readPost.call("{\"postId\":\"post-1\"}");

		assertThat(result).isInstanceOfSatisfying(Tool.Result.WithArtifact.class, response -> {
			assertThat(response.getContent()).contains("post-1", "untrusted content", "pinbabel-fixture");
			assertThat(response.getArtifact()).isInstanceOf(AnalysisWorkspaceTools.PostReadResult.class);
		});
	}

	@Test
	void repeatedToolCallsWithTheSameWorkspaceInputAreDeterministic() {
		var workspace = workspace(
			List.of(post("post-1", "untrusted content")),
			List.of(instrument("NASDAQ", "NVDA"))
		);

		assertThat(workspace.listPosts()).isEqualTo(workspace.listPosts());
		assertThat(workspace.readPost("post-1")).isEqualTo(workspace.readPost("post-1"));
		assertThat(workspace.searchInstruments("nvda")).isEqualTo(workspace.searchInstruments("nvda"));
		assertThat(workspace.readInstrument("NASDAQ:NVDA"))
			.isEqualTo(workspace.readInstrument("NASDAQ:NVDA"));
	}

	private AnalysisWorkspaceTools workspace(List<CollectedPost> posts, List<InstrumentReference> instruments) {
		return new AnalysisWorkspaceTools(
			new CollectedPosts(posts),
			Set.of("NASDAQ"),
			new RecordingInstrumentCatalog(instruments)
		);
	}

	private List<Tool.Parameter> parameters(Map<String, Tool> toolsByName, String toolName) {
		return toolsByName.get(toolName).getDefinition().getInputSchema().getParameters();
	}

	private CollectedPost post(String postId, String text) {
		return new CollectedPost(
			postId,
			"fixture-social",
			"0007-market-voice",
			Instant.parse("2026-01-01T00:00:00Z").plusSeconds(numericSuffix(postId)),
			URI.create("https://social.example/posts/" + postId),
			text,
			"pinbabel-fixture",
			PostKind.ORIGINAL
		);
	}

	private int numericSuffix(String postId) {
		var suffix = postId.substring(postId.lastIndexOf('-') + 1);
		return suffix.chars().allMatch(Character::isDigit) ? Integer.parseInt(suffix) : 0;
	}

	private InstrumentReference instrument(String exchange, String ticker) {
		return new InstrumentReference(
			exchange + ":" + ticker,
			ticker,
			exchange,
			ticker + " Corporation",
			List.of(ticker.toLowerCase(Locale.ROOT))
		);
	}

	private static final class RecordingInstrumentCatalog implements InstrumentCatalog {

		private final List<InstrumentReference> instruments;
		private String lastQuery;
		private Set<String> lastMarkets;
		private int lastLimit;

		private RecordingInstrumentCatalog(List<InstrumentReference> instruments) {
			this.instruments = List.copyOf(instruments);
		}

		@Override
		public List<InstrumentReference> search(String query, Set<String> marketCodes, int limit) {
			lastQuery = query;
			lastMarkets = Set.copyOf(marketCodes);
			lastLimit = limit;
			return instruments;
		}

		@Override
		public Optional<InstrumentReference> findById(String instrumentId) {
			return instruments.stream()
				.filter(instrument -> instrument.instrumentId().equalsIgnoreCase(instrumentId))
				.findFirst();
		}
	}
}
