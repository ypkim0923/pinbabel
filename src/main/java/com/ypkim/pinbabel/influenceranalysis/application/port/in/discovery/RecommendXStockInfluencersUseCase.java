package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface RecommendXStockInfluencersUseCase {

	XStockInfluencerRecommendationsResource recommend();
}
