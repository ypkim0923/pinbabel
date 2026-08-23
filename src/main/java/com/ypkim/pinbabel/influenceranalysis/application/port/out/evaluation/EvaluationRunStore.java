package com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface EvaluationRunStore {

	void save(EvaluationRun run);

	List<EvaluationRun> findLatest(int limit);

	Optional<EvaluationRun> findById(EvaluationRunId runId);
}
