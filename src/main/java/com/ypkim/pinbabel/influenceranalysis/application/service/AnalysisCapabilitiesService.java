package com.ypkim.pinbabel.influenceranalysis.application.service;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.QueryAnalysisCapabilitiesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisCapabilitiesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture")
public class AnalysisCapabilitiesService implements QueryAnalysisCapabilitiesUseCase {

	private final SocialPostSource socialPostSource;

	public AnalysisCapabilitiesService(SocialPostSource socialPostSource) {
		this.socialPostSource = socialPostSource;
	}

	@Override
	public AnalysisCapabilitiesResource capabilities() {
		return new AnalysisCapabilitiesResource(socialPostSource.supportedPlatforms());
	}
}
