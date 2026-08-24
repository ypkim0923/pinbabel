package com.ypkim.pinbabel.influenceranalysis.application.service.tool;

import com.embabel.agent.api.annotation.LlmTool;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.RecommendXStockInfluencersUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("fixture")
public class StockInfluencerDiscoveryTools {

	private final RecommendXStockInfluencersUseCase recommendUseCase;

	public StockInfluencerDiscoveryTools(RecommendXStockInfluencersUseCase recommendUseCase) {
		this.recommendUseCase = recommendUseCase;
	}

	@LlmTool(
		name = "recommend_x_stock_influencers",
		description = "Return a bounded user-curated list of X stock-related accounts without calling X API or an LLM. This is not a live popularity ranking."
	)
	public XStockInfluencerRecommendationsResource recommendXStockInfluencers() {
		return recommendUseCase.recommend();
	}
}
