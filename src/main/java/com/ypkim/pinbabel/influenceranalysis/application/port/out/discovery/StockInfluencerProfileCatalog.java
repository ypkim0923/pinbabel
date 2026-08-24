package com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.StockInfluencerProfile;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface StockInfluencerProfileCatalog {

	List<StockInfluencerProfile> findAll();

	Optional<StockInfluencerProfile> findById(InfluencerProfileId profileId);
}
