package com.ypkim.pinbabel.influenceranalysis.application.port.in.dto;

import java.util.Set;

public record AnalysisCapabilitiesResource(Set<String> supportedPlatforms) {

	public AnalysisCapabilitiesResource {
		supportedPlatforms = Set.copyOf(supportedPlatforms);
	}
}
