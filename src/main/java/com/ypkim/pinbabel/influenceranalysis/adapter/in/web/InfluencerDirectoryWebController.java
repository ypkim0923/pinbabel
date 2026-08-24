package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryFixtureStockInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryStockInfluencerProfilesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.RecommendXStockInfluencersUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import jakarta.servlet.http.HttpSession;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
@Profile("fixture & web")
@PrimaryAdapter
public class InfluencerDirectoryWebController {

	private final RecommendXStockInfluencersUseCase recommendUseCase;
	private final QueryStockInfluencerProfilesUseCase profileUseCase;
	private final QueryFixtureStockInfluencerAnalysisUseCase fixtureAnalysisUseCase;
	private final InfluencerAnalysisWebViewMapper mapper;
	private final SsrLiveAnalysisAvailability liveAvailability;
	private final SsrExecutionIntentTokenManager tokenManager;

	public InfluencerDirectoryWebController(
		RecommendXStockInfluencersUseCase recommendUseCase,
		QueryStockInfluencerProfilesUseCase profileUseCase,
		QueryFixtureStockInfluencerAnalysisUseCase fixtureAnalysisUseCase,
		InfluencerAnalysisWebViewMapper mapper,
		SsrLiveAnalysisAvailability liveAvailability,
		SsrExecutionIntentTokenManager tokenManager
	) {
		this.recommendUseCase = recommendUseCase;
		this.profileUseCase = profileUseCase;
		this.fixtureAnalysisUseCase = fixtureAnalysisUseCase;
		this.mapper = mapper;
		this.liveAvailability = liveAvailability;
		this.tokenManager = tokenManager;
	}

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("directory", null);
		return "influenceranalysis/home";
	}

	@GetMapping("/influencers")
	public String influencers(
		@RequestHeader(name = "HX-Request", required = false) String htmxRequest,
		Model model
	) {
		model.addAttribute("directory", mapper.directory(recommendUseCase.recommend()));
		return "true".equalsIgnoreCase(htmxRequest)
			? "influenceranalysis/fragments/profile-list :: profileList"
			: "influenceranalysis/home";
	}

	@GetMapping("/influencers/{profileId}")
	public String profile(
		@PathVariable String profileId,
		HttpSession session,
		Model model
	) {
		var profile = findProfile(profileId);
		var analysis = "FIXTURE".equals(profile.sourceType())
			? fixtureAnalysisUseCase.findAnalysis(new InfluencerProfileId(profileId))
				.map(result -> mapper.fixture(profileId, result))
				.orElseThrow(SsrRequestException::profileNotFound)
			: null;
		var token = "LIVE_X".equals(profile.sourceType()) && liveAvailability.enabled()
			? tokenManager.issue(session)
			: null;
		model.addAttribute("page", mapper.page(
			profile,
			analysis,
			liveAvailability.enabled(),
			liveAvailability.setupGuidance(),
			token,
			InfluencerAnalysisOutcome.DISCLAIMER
		));
		return "influenceranalysis/detail";
	}

	private XStockInfluencerRecommendationResource findProfile(String rawProfileId) {
		final InfluencerProfileId profileId;
		try {
			profileId = new InfluencerProfileId(rawProfileId);
		}
		catch (IllegalArgumentException exception) {
			throw SsrRequestException.invalidProfileId();
		}
		return profileUseCase.findProfile(profileId).orElseThrow(SsrRequestException::profileNotFound);
	}
}
