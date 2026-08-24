package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileSource;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.StockInfluencerProfile;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryStockInfluencerProfilesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.RecommendXStockInfluencersUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.StockInfluencerProfileCatalog;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture")
public class StockInfluencerRecommendationService implements RecommendXStockInfluencersUseCase, QueryStockInfluencerProfilesUseCase {

	private final StockInfluencerProfileCatalog catalog;

	public StockInfluencerRecommendationService(StockInfluencerProfileCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public XStockInfluencerRecommendationsResource recommend() {
		return new XStockInfluencerRecommendationsResource(
			"실시간 인기 순위가 아니라 Pinbabel이 관리하는 실험용 추천 목록입니다.",
			false,
			false,
			catalog.findAll().stream()
				.map(this::toResource)
				.toList()
		);
	}

	@Override
	public Optional<XStockInfluencerRecommendationResource> findProfile(InfluencerProfileId profileId) {
		return catalog.findById(profileId).map(this::toResource);
	}

	private XStockInfluencerRecommendationResource toResource(StockInfluencerProfile profile) {
		var source = profile.source();
		return new XStockInfluencerRecommendationResource(
			profile.id().value(),
			"x",
			profile.handle().displayHandle(),
			profile.displayName(),
			profile.description(),
			source == InfluencerProfileSource.LIVE_X ? "USER_CURATED" : "PINBABEL_FIXTURE",
			profile.investmentStyle(),
			source.name(),
			profile.avatarInitials(),
			profile.avatarColor()
		);
	}
}
