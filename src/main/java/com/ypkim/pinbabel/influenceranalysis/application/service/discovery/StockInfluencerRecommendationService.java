package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.RecommendXStockInfluencersUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture")
public class StockInfluencerRecommendationService implements RecommendXStockInfluencersUseCase {

	private static final XStockInfluencerRecommendationResource SERENITY =
		new XStockInfluencerRecommendationResource(
			"x",
			"@aleabitoreddit",
			"Serenity",
			"Pinbabel 초기 실험을 위해 사용자가 지정한 주식 관련 공개 계정",
			"USER_CURATED"
		);

	@Override
	public XStockInfluencerRecommendationsResource recommend() {
		return new XStockInfluencerRecommendationsResource(
			"실시간 인기 순위가 아니라 사용자가 지정한 초기 추천 목록입니다.",
			false,
			false,
			List.of(SERENITY)
		);
	}
}
