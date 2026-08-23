package com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunSummaryResource;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface QueryEvaluationRunsUseCase {

	int RECENT_RUN_LIMIT = 20;

	List<EvaluationRunSummaryResource> recentRuns();

	Optional<EvaluationRunDetailResource> findRun(EvaluationRunId runId);
}
