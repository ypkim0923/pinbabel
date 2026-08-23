package com.ypkim.pinbabel.influenceranalysis.application.port.out;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface SocialPostSource {

	CollectedPosts findPosts(InfluencerAnalysisRequest request);
}
