package com.ypkim.pinbabel.influenceranalysis.application.service.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisProgressEventResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AnalysisRunQueryService implements QueryAnalysisRunsUseCase {

	private final AnalysisRunStore store;

	public AnalysisRunQueryService(AnalysisRunStore store) {
		this.store = store;
	}

	@Override
	public List<AnalysisRunSummaryResource> recentRuns() {
		return store.findLatest(RECENT_RUN_LIMIT).stream()
			.map(stored -> new AnalysisRunSummaryResource(
				stored.runId(),
				stored.correlationId(),
				stored.status().name(),
				stored.createdAt(),
				stored.startedAt(),
				stored.durationMs(),
				stored.traceAvailable()
			))
			.toList();
	}

	@Override
	public Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId) {
		return store.findById(runId).map(stored -> new AnalysisRunDetailResource(
			stored.runId(),
			stored.correlationId(),
			stored.status().name(),
			stored.createdAt(),
			stored.startedAt(),
			stored.completedAt(),
			stored.durationMs(),
			stored.traceAvailable(),
			stored.warningCode(),
			stored.outcomeCode(),
			stored.outcomeSummary(),
			stored.metrics(),
			stored.report(),
			stored.events().stream().map(AnalysisProgressEventResource::from).toList()
		));
	}
}
