package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryStockInfluencerProfilesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;

class RecentXAnalysisWebControllerTest {
	private static final String RUN_ID = "3c2c33e5-23d8-4ecb-b9d1-98587e916c94";

	@Test
	void consumesOneIntentTokenAndSubmitsExactlyOnce() {
		var submitCalls = new AtomicInteger();
		SubmitRecentXAnalysisUseCase submit = account -> {
			submitCalls.incrementAndGet();
			return new AnalysisSubmissionResource(RUN_ID, "correlation", "CREATED", Instant.now(), null, null);
		};
		var session = new MockHttpSession();
		var tokenManager = new SsrExecutionIntentTokenManager();
		var token = tokenManager.issue(session);
		var controller = controller(submit, tokenManager, "@aleabitoreddit");

		var view = controller.submit("serenity", token, "true", session, new ConcurrentModel());

		assertThat(view).contains("analysis-panel");
		assertThat(submitCalls).hasValue(1);
		assertThatThrownBy(() -> controller.submit(
			"serenity", token, "true", session, new ConcurrentModel()
		)).isInstanceOf(SsrRequestException.class);
		assertThat(submitCalls).hasValue(1);
	}

	@Test
	void rejectsFixtureProfilesBeforeSubmitting() {
		var submitCalls = new AtomicInteger();
		SubmitRecentXAnalysisUseCase submit = account -> {
			submitCalls.incrementAndGet();
			return null;
		};
		var tokenManager = new SsrExecutionIntentTokenManager();
		var session = new MockHttpSession();
		var token = tokenManager.issue(session);
		var controller = controller(submit, tokenManager, "@aleabitoreddit");

		assertThatThrownBy(() -> controller.submit(
			"growth-lab", token, "true", session, new ConcurrentModel()
		)).isInstanceOf(SsrRequestException.class);
		assertThat(submitCalls).hasValue(0);
	}

	private RecentXAnalysisWebController controller(
		SubmitRecentXAnalysisUseCase submit,
		SsrExecutionIntentTokenManager tokenManager,
		String storedAccount
	) {
		QueryRecentXAnalysisUseCase query = runId -> Optional.of(new RecentXAnalysisDetailResource(
			runId.value(), "correlation", "CREATED", Instant.now(), null, null,
			null, null, null, emptyResult(storedAccount)
		));
		QueryStockInfluencerProfilesUseCase profiles = profileId -> {
			if ("serenity".equals(profileId.value())) {
				return Optional.of(profile("serenity", "LIVE_X", "@aleabitoreddit"));
			}
			if ("growth-lab".equals(profileId.value())) {
				return Optional.of(profile("growth-lab", "FIXTURE", "@pin_growthlab"));
			}
			return Optional.empty();
		};
		var mapper = new InfluencerAnalysisWebViewMapper(new SsrAnalysisFailurePresenter());
		return new RecentXAnalysisWebController(submit, query, profiles, mapper, tokenManager);
	}

	private XStockInfluencerRecommendationResource profile(String id, String source, String handle) {
		return new XStockInfluencerRecommendationResource(
			id, "x", handle, "Profile", "description", "USER_CURATED", "style", source, "PX", "teal"
		);
	}

	private RecentMentionedCompaniesResource emptyResult(String account) {
		return new RecentMentionedCompaniesResource(
			"CREATED", "waiting", account, 0, true, true, false,
			0, 0, 2, 1, 0, List.of(), List.of(), "not advice"
		);
	}
}
