package com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredRecentXAnalysisResult;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface RecentXAnalysisResultStore {

	void save(AnalysisRunId runId, StoredRecentXAnalysisResult result);

	Optional<StoredRecentXAnalysisResult> findByRunId(AnalysisRunId runId);
}
