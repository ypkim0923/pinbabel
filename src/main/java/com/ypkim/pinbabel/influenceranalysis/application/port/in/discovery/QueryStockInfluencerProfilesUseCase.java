package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface QueryStockInfluencerProfilesUseCase {

	Optional<XStockInfluencerRecommendationResource> findProfile(InfluencerProfileId profileId);
}
