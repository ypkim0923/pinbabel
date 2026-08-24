package com.ypkim.pinbabel.influenceranalysis.application.port.in;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisCapabilitiesResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface QueryAnalysisCapabilitiesUseCase {

	AnalysisCapabilitiesResource capabilities();
}
