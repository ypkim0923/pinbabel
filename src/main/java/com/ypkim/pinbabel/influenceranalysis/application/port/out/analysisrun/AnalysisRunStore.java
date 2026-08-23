package com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface AnalysisRunStore {

	void save(AnalysisRun run, InfluencerAnalysisReport report);

	void append(AnalysisRunId runId, AnalysisTraceEvent event);

	List<StoredAnalysisRunSummary> findLatest(int limit);

	Optional<StoredAnalysisRunDetail> findById(AnalysisRunId runId);
}
