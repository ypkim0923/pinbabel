package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryStockInfluencerProfilesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import jakarta.servlet.http.HttpSession;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Profile("fixture & x & web")
@PrimaryAdapter
public class RecentXAnalysisWebController {

	private final SubmitRecentXAnalysisUseCase submitUseCase;
	private final QueryRecentXAnalysisUseCase queryUseCase;
	private final QueryStockInfluencerProfilesUseCase profileUseCase;
	private final InfluencerAnalysisWebViewMapper mapper;
	private final SsrExecutionIntentTokenManager tokenManager;

	public RecentXAnalysisWebController(
		SubmitRecentXAnalysisUseCase submitUseCase,
		QueryRecentXAnalysisUseCase queryUseCase,
		QueryStockInfluencerProfilesUseCase profileUseCase,
		InfluencerAnalysisWebViewMapper mapper,
		SsrExecutionIntentTokenManager tokenManager
	) {
		this.submitUseCase = submitUseCase;
		this.queryUseCase = queryUseCase;
		this.profileUseCase = profileUseCase;
		this.mapper = mapper;
		this.tokenManager = tokenManager;
	}

	@PostMapping("/influencers/{profileId}/analyses")
	public String submit(
		@PathVariable String profileId,
		@RequestParam(name = "executionToken") String executionToken,
		@RequestHeader(name = "HX-Request", required = false) String htmxRequest,
		HttpSession session,
		Model model
	) {
		var profile = requireLiveProfile(profileId);
		if (!tokenManager.consume(session, executionToken)) {
			throw SsrRequestException.invalidExecutionToken();
		}
		var submission = submitUseCase.submit(profile.handle());
		if (!"true".equalsIgnoreCase(htmxRequest)) {
			return "redirect:/influencers/" + profileId + "/analyses/" + submission.runId();
		}
		var detail = queryUseCase.findRecentRun(new AnalysisRunId(submission.runId()))
			.orElseThrow(SsrRequestException::runNotFound);
		model.addAttribute("page", mapper.page(
			profile, mapper.live(detail), true, "", null, InfluencerAnalysisOutcome.DISCLAIMER
		));
		return "influenceranalysis/fragments/analysis-panel :: analysisPanel";
	}

	@GetMapping("/influencers/{profileId}/analyses/{runId}")
	public String status(
		@PathVariable String profileId,
		@PathVariable String runId,
		@RequestHeader(name = "HX-Request", required = false) String htmxRequest,
		HttpSession session,
		Model model
	) {
		var profile = requireLiveProfile(profileId);
		final AnalysisRunId analysisRunId;
		try {
			analysisRunId = new AnalysisRunId(runId);
		}
		catch (IllegalArgumentException exception) {
			throw SsrRequestException.invalidRunId();
		}
		var detail = queryUseCase.findRecentRun(analysisRunId).orElseThrow(SsrRequestException::runNotFound);
		if (detail.result() == null || !profile.handle().equalsIgnoreCase(detail.result().account())) {
			throw SsrRequestException.runAccountMismatch();
		}
		var analysis = mapper.live(detail);
		var token = analysis.retryAllowed() ? tokenManager.issue(session) : null;
		model.addAttribute("page", mapper.page(
			profile, analysis, true, "", token, InfluencerAnalysisOutcome.DISCLAIMER
		));
		return "true".equalsIgnoreCase(htmxRequest)
			? "influenceranalysis/fragments/analysis-panel :: analysisPanel"
			: "influenceranalysis/detail";
	}

	private XStockInfluencerRecommendationResource requireLiveProfile(String rawProfileId) {
		final InfluencerProfileId profileId;
		try {
			profileId = new InfluencerProfileId(rawProfileId);
		}
		catch (IllegalArgumentException exception) {
			throw SsrRequestException.invalidProfileId();
		}
		var profile = profileUseCase.findProfile(profileId).orElseThrow(SsrRequestException::profileNotFound);
		if (!"LIVE_X".equals(profile.sourceType()) || !"serenity".equals(profile.profileId())) {
			throw SsrRequestException.profileNotFound();
		}
		return profile;
	}
}
