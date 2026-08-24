package com.ypkim.pinbabel.influenceranalysis.application.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.agent.api.tool.Tool;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StockInfluencerDiscoveryToolsTest {

	@Test
	void exposesOneParameterlessBoundedEmbabelTool() {
		var tools = Tool.fromInstance(new StockInfluencerDiscoveryTools(this::recommendations), new ObjectMapper());

		assertThat(tools).singleElement().satisfies(tool -> {
			assertThat(tool.getDefinition().getName()).isEqualTo("recommend_x_stock_influencers");
			assertThat(tool.getDefinition().getDescription())
				.contains("without calling X API", "not a live popularity ranking");
			assertThat(tool.getDefinition().getInputSchema().getParameters()).isEmpty();
		});
	}

	@Test
	void returnsStructuredContentAndTypedArtifactWithoutExternalCalls() {
		var tool = Tool.fromInstance(new StockInfluencerDiscoveryTools(this::recommendations), new ObjectMapper())
			.getFirst();

		var result = tool.call("{}");

		assertThat(result).isInstanceOfSatisfying(Tool.Result.WithArtifact.class, response -> {
			assertThat(response.getContent())
				.contains("@aleabitoreddit", "USER_CURATED", "\"xApiUsed\":false", "\"llmUsed\":false");
			assertThat(response.getArtifact()).isEqualTo(recommendations());
		});
	}

	private XStockInfluencerRecommendationsResource recommendations() {
		return new XStockInfluencerRecommendationsResource(
			"curated",
			false,
			false,
			List.of(new XStockInfluencerRecommendationResource(
				"serenity", "x", "@aleabitoreddit", "Serenity", "user curated", "USER_CURATED",
				"technology growth", "LIVE_X", "SE", "teal"
			))
		);
	}
}
