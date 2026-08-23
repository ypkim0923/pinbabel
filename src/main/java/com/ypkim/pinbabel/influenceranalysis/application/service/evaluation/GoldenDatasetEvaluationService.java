package com.ypkim.pinbabel.influenceranalysis.application.service.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationCaseResult;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.GoldenDatasetEvaluator;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.EvaluateGoldenDatasetUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.EvaluationRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.GoldenDatasetSource;
import java.time.Clock;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture & cli")
public class GoldenDatasetEvaluationService implements EvaluateGoldenDatasetUseCase {

	private final GoldenDatasetSource datasetSource;
	private final AnalyzeInfluencerPostsUseCase analysisUseCase;
	private final EvaluationRunStore runStore;
	private final GoldenDatasetEvaluator evaluator = new GoldenDatasetEvaluator();
	private final Clock clock;

	@Autowired
	public GoldenDatasetEvaluationService(
		GoldenDatasetSource datasetSource,
		AnalyzeInfluencerPostsUseCase analysisUseCase,
		EvaluationRunStore runStore
	) {
		this(datasetSource, analysisUseCase, runStore, Clock.systemUTC());
	}

	GoldenDatasetEvaluationService(
		GoldenDatasetSource datasetSource,
		AnalyzeInfluencerPostsUseCase analysisUseCase,
		EvaluationRunStore runStore,
		Clock clock
	) {
		this.datasetSource = datasetSource;
		this.analysisUseCase = analysisUseCase;
		this.runStore = runStore;
		this.clock = clock;
	}

	@Override
	public EvaluationRunDetailResource evaluate() {
		var dataset = datasetSource.load();
		var id = EvaluationRunId.newId();
		var createdAt = clock.instant();
		var startedAt = clock.instant();
		var results = new ArrayList<EvaluationCaseResult>();
		for (var goldenCase : dataset.cases()) {
			try {
				var analysis = analysisUseCase.analyze(new AnalyzeInfluencerPostsCommand(goldenCase.instruction()));
				results.add(evaluator.evaluate(
					goldenCase, analysis.runId(), analysis.status(), analysis.report()
				));
			}
			catch (RuntimeException exception) {
				results.add(evaluator.evaluate(goldenCase, null, "FAILED", null));
			}
		}
		var run = EvaluationRun.completed(id, dataset, createdAt, startedAt, clock.instant(), results);
		runStore.save(run);
		return EvaluationRunDetailResource.from(run);
	}
}
