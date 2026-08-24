package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui;

import com.ypkim.pinbabel.influenceranalysis.adapter.in.rest.PinbabelApiException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
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
@RequestMapping("/a2ui/v0.9/x-influencer-analyses")
@Profile("fixture & x & api")
@PrimaryAdapter
public class A2UiRecentXAnalysisController {

	private final SubmitRecentXAnalysisUseCase submitUseCase;
	private final QueryRecentXAnalysisUseCase queryUseCase;
	private final A2UiRecentXSnapshotRenderer renderer;

	public A2UiRecentXAnalysisController(
		SubmitRecentXAnalysisUseCase submitUseCase,
		QueryRecentXAnalysisUseCase queryUseCase,
		A2UiRecentXSnapshotRenderer renderer
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.renderer = renderer;
	}

	@PostMapping(produces = A2UiAnalysisController.NDJSON)
	public ResponseEntity<String> create(@Valid @RequestBody A2UiRecentXAnalysisRequest request) {
		var submission = submitUseCase.submit(request.account());
		var status = "REJECTED".equals(submission.status()) ? 400 : 202;
		if ("EXECUTION_CAPACITY_EXCEEDED".equals(submission.outcomeCode())) status = 429;
		return ResponseEntity.status(status)
			.contentType(MediaType.parseMediaType(A2UiAnalysisController.NDJSON))
			.body(renderer.render(find(submission.runId())));
	}

	@GetMapping(value = "/{runId}", produces = A2UiAnalysisController.NDJSON)
	public String get(@PathVariable String runId) {
		return renderer.render(find(runId));
	}

	private RecentXAnalysisDetailResource find(String runId) {
		final AnalysisRunId id;
		try {
			id = new AnalysisRunId(runId);
		}
		catch (IllegalArgumentException exception) {
			throw PinbabelApiException.invalidRunId();
		}
		return queryUseCase.findRecentRun(id).orElseThrow(PinbabelApiException::runNotFound);
	}
}
