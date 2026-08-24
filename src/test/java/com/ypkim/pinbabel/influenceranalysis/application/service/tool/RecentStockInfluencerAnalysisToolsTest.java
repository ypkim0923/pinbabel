package com.ypkim.pinbabel.influenceranalysis.application.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.embabel.agent.api.tool.Tool;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RecentStockInfluencerAnalysisToolsTest {

	@Test
	void exposesTwoBoundedEmbabelToolsWithOneRequiredAccountParameter() {
		var tools = Tool.fromInstance(new RecentStockInfluencerAnalysisTools(useCase()), new ObjectMapper());
		var byName = tools.stream().collect(Collectors.toMap(
			tool -> tool.getDefinition().getName(),
			tool -> tool
		));

		assertThat(tools).extracting(tool -> tool.getDefinition().getName())
			.containsExactlyInAnyOrder(
				"list_recent_x_mentioned_companies",
				"analyze_recent_x_company_sentiment"
			);
		assertThat(byName).allSatisfy((name, tool) -> {
			assertThat(tool.getDefinition().getDescription()).contains("ten most recent");
			assertThat(tool.getDefinition().getInputSchema().getParameters())
				.extracting(Tool.Parameter::getName, Tool.Parameter::getRequired)
				.containsExactly(tuple("account", true));
		});
	}

	@Test
	void returnsStructuredArtifactsWithCostAndExclusionMetadata() {
		var tools = Tool.fromInstance(new RecentStockInfluencerAnalysisTools(useCase()), new ObjectMapper());
		var mentioned = tool(tools, "list_recent_x_mentioned_companies").call("{\"account\":\"@aleabitoreddit\"}");
		var sentiment = tool(tools, "analyze_recent_x_company_sentiment").call("{\"account\":\"@aleabitoreddit\"}");

		assertThat(mentioned).isInstanceOfSatisfying(Tool.Result.WithArtifact.class, response -> {
			assertThat(response.getContent())
				.contains("@aleabitoreddit", "\"commentsExcluded\":true", "\"xApiRequestsThisCall\":2")
				.contains("\"xApiRequestBudget\":2", "\"llmCallBudget\":1")
				.contains("\"sourceUrl\":\"https://x.com/example/status/post-1\"")
				.contains("\"rationale\":\"Looks strong\"", "\"confidence\":0.91");
			assertThat(response.getArtifact()).isInstanceOf(RecentMentionedCompaniesResource.class);
		});
		assertThat(sentiment).isInstanceOfSatisfying(Tool.Result.WithArtifact.class, response -> {
			assertThat(response.getContent()).contains("positiveCompanies", "negativeCompanies");
			assertThat(response.getArtifact()).isInstanceOf(RecentCompanySentimentResource.class);
		});
	}

	private Tool tool(List<Tool> tools, String name) {
		return tools.stream().filter(tool -> tool.getDefinition().getName().equals(name)).findFirst().orElseThrow();
	}

	private AnalyzeRecentXStockInfluencerUseCase useCase() {
		return new AnalyzeRecentXStockInfluencerUseCase() {
			@Override
			public RecentMentionedCompaniesResource mentionedCompanies(String account) {
				return new RecentMentionedCompaniesResource(
					"COMPLETED", "done", account, 10, true, true, false, 2, 1, 2, 1, 100,
					List.of(company()), List.of(), "not investment advice"
				);
			}

			@Override
			public RecentCompanySentimentResource companySentiment(String account) {
				return new RecentCompanySentimentResource(
					"COMPLETED", "done", account, 10, true, true, true, 0, 0, 2, 1, 0,
					List.of(), List.of(), List.of(), "not investment advice"
				);
			}
		};
	}

	private RecentCompanyResource company() {
		return new RecentCompanyResource(
			"Microsoft", "POSITIVE", 1, 0, 0, 0, false, 0.91,
			List.of(new RecentCompanyEvidenceResource(
				"post-1",
				Instant.parse("2026-08-24T00:00:00Z"),
				URI.create("https://x.com/example/status/post-1"),
				"Microsoft looks strong",
				"POSITIVE",
				"Looks strong",
				0.91
			))
		);
	}
}
