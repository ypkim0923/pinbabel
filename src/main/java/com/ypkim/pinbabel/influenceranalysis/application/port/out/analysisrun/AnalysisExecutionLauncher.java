package com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface AnalysisExecutionLauncher {

	boolean launch(AnalysisRunId runId, Runnable execution);
}
