package com.ypkim.pinbabel.influenceranalysis.application.port.in;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface SubmitInfluencerAnalysisUseCase {

	AnalysisSubmissionResource submit(SubmitInfluencerAnalysisCommand command);
}
