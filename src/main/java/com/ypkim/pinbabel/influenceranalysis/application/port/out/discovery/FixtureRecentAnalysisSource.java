package com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.dto.FixtureRecentAnalysisScenario;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface FixtureRecentAnalysisSource {

	Optional<FixtureRecentAnalysisScenario> findByProfileId(InfluencerProfileId profileId);
}
