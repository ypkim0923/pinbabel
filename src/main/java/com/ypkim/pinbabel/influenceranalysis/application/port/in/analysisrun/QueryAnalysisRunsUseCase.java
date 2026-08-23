package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface QueryAnalysisRunsUseCase {

	int RECENT_RUN_LIMIT = 20;

	List<AnalysisRunSummaryResource> recentRuns();

	Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId);
}
