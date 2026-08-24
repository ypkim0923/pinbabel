package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/influencer-analyses")
@Profile("fixture & api")
@PrimaryAdapter
public class InfluencerAnalysisRestController {

	private final SubmitInfluencerAnalysisUseCase submitUseCase;
	private final QueryAnalysisRunsUseCase queryUseCase;

	public InfluencerAnalysisRestController(
		SubmitInfluencerAnalysisUseCase submitUseCase,
		QueryAnalysisRunsUseCase queryUseCase
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
	}

	@PostMapping
	public ResponseEntity<InfluencerAnalysisSubmissionResponse> create(
		@Valid @RequestBody InfluencerAnalysisCreateRequest request
	) {
		var resource = submitUseCase.submit(request.toCommand());
		var response = InfluencerAnalysisSubmissionResponse.from(resource);
		if ("REJECTED".equals(resource.status())) {
			var status = "EXECUTION_CAPACITY_EXCEEDED".equals(resource.outcomeCode()) ? 429 : 400;
			return ResponseEntity.status(status).body(response);
		}
		return ResponseEntity.accepted()
			.location(URI.create("/api/v1/influencer-analyses/" + resource.runId()))
			.body(response);
	}

	@GetMapping("/{runId}")
	public InfluencerAnalysisDetailResponse get(@PathVariable String runId) {
		final AnalysisRunId id;
		try {
			id = new AnalysisRunId(runId);
		} catch (IllegalArgumentException exception) {
			throw PinbabelApiException.invalidRunId();
		}
		return queryUseCase.findRun(id)
			.map(InfluencerAnalysisDetailResponse::from)
			.orElseThrow(PinbabelApiException::runNotFound);
	}
}
