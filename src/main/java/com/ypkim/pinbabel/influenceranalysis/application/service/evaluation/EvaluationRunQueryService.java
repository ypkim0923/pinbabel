package com.ypkim.pinbabel.influenceranalysis.application.service.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.QueryEvaluationRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.EvaluationRunStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EvaluationRunQueryService implements QueryEvaluationRunsUseCase {

	private final EvaluationRunStore store;

	public EvaluationRunQueryService(EvaluationRunStore store) {
		this.store = store;
	}

	@Override
	public List<EvaluationRunSummaryResource> recentRuns() {
		return store.findLatest(RECENT_RUN_LIMIT).stream()
			.map(EvaluationRunDetailResource::from)
			.map(EvaluationRunDetailResource::toSummary)
			.toList();
	}

	@Override
	public Optional<EvaluationRunDetailResource> findRun(EvaluationRunId runId) {
		return store.findById(runId).map(EvaluationRunDetailResource::from);
	}
}
