package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface SubmitRecentXAnalysisUseCase {

	AnalysisSubmissionResource submit(String account);
}
