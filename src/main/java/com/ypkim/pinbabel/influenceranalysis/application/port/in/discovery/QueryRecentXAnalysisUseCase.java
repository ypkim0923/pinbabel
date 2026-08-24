package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface QueryRecentXAnalysisUseCase {

	Optional<RecentXAnalysisDetailResource> findRecentRun(AnalysisRunId runId);
}
