package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
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
@RequestMapping("/api/v1/x-influencer-analyses")
@Profile("fixture & x & api")
@PrimaryAdapter
public class RecentXAnalysisRestController {

	private final SubmitRecentXAnalysisUseCase submitUseCase;
	private final QueryRecentXAnalysisUseCase queryUseCase;

	public RecentXAnalysisRestController(SubmitRecentXAnalysisUseCase submitUseCase, QueryRecentXAnalysisUseCase queryUseCase) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
	}

	@PostMapping
	public ResponseEntity<InfluencerAnalysisSubmissionResponse> create(@Valid @RequestBody RecentXAnalysisCreateRequest request) {
		var resource = submitUseCase.submit(request.account());
		var response = InfluencerAnalysisSubmissionResponse.from(resource);
		if ("REJECTED".equals(resource.status())) {
			var status = "EXECUTION_CAPACITY_EXCEEDED".equals(resource.outcomeCode()) ? 429 : 400;
			return ResponseEntity.status(status).body(response);
		}
		return ResponseEntity.accepted()
			.location(URI.create("/api/v1/x-influencer-analyses/" + resource.runId()))
			.body(response);
	}

	@GetMapping("/{runId}")
	public RecentXAnalysisDetailResponse get(@PathVariable String runId) {
		final AnalysisRunId id;
		try {
			id = new AnalysisRunId(runId);
		}
		catch (IllegalArgumentException exception) {
			throw PinbabelApiException.invalidRunId();
		}
		return queryUseCase.findRecentRun(id)
			.map(RecentXAnalysisDetailResponse::from)
			.orElseThrow(PinbabelApiException::runNotFound);
	}
}
