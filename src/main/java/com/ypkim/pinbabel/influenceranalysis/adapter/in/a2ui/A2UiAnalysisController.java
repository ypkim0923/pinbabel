package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui;

import com.ypkim.pinbabel.influenceranalysis.adapter.in.rest.PinbabelApiException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import jakarta.validation.Valid;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/a2ui/v0.9/analyses")
@Profile("fixture & api")
@PrimaryAdapter
public class A2UiAnalysisController {
	public static final String NDJSON = "application/x-ndjson";

	private final SubmitInfluencerAnalysisUseCase submitUseCase;
	private final QueryAnalysisRunsUseCase queryUseCase;
	private final A2UiSnapshotRenderer renderer;

	public A2UiAnalysisController(
		SubmitInfluencerAnalysisUseCase submitUseCase,
		QueryAnalysisRunsUseCase queryUseCase,
		A2UiSnapshotRenderer renderer
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.renderer = renderer;
	}

	@PostMapping(produces = NDJSON)
	public ResponseEntity<String> create(@Valid @RequestBody A2UiAnalysisRequest request) {
		var submission = submitUseCase.submit(request.toCommand());
		var run = find(submission.runId());
		var status = "REJECTED".equals(submission.status()) ? 400 : 202;
		if ("EXECUTION_CAPACITY_EXCEEDED".equals(submission.outcomeCode())) status = 429;
		return ResponseEntity.status(status)
			.contentType(MediaType.parseMediaType(NDJSON))
			.body(renderer.render(run));
	}

	@GetMapping(value = "/{runId}", produces = NDJSON)
	public String get(@PathVariable String runId) {
		return renderer.render(find(runId));
	}

	private AnalysisRunDetailResource find(String runId) {
		final AnalysisRunId id;
		try {
			id = new AnalysisRunId(runId);
		} catch (IllegalArgumentException exception) {
			throw PinbabelApiException.invalidRunId();
		}
		return queryUseCase.findRun(id).orElseThrow(PinbabelApiException::runNotFound);
	}
}
