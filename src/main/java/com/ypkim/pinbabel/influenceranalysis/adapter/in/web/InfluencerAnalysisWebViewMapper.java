package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.InfluencerDirectoryViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.InfluencerProfilePageViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.InfluencerProfileViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.RecentAnalysisViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.RecentAnalysisViewModel.CompanyViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.RecentAnalysisViewModel.EvidenceViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.RecentAnalysisViewModel.ExperimentViewModel;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view.RecentAnalysisViewModel.SentimentSectionViewModel;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.XStockInfluencerRecommendationsResource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("web")
@PrimaryAdapter
public class InfluencerAnalysisWebViewMapper {

	static final Duration AUTOMATIC_POLLING_BUDGET = Duration.ofSeconds(120);
	private static final List<SentimentDefinition> SENTIMENTS = List.of(
		new SentimentDefinition("POSITIVE", "긍정", "긍정적으로 평가한 회사가 없습니다."),
		new SentimentDefinition("NEGATIVE", "부정", "부정적으로 평가한 회사가 없습니다."),
		new SentimentDefinition("NEUTRAL", "중립", "중립적으로 언급한 회사가 없습니다."),
		new SentimentDefinition("UNCERTAIN", "판단 불가", "판단을 유보한 회사가 없습니다.")
	);

	private final SsrAnalysisFailurePresenter failurePresenter;
	private final Clock clock;

	@Autowired
	public InfluencerAnalysisWebViewMapper(SsrAnalysisFailurePresenter failurePresenter) {
		this(failurePresenter, Clock.systemUTC());
	}

	InfluencerAnalysisWebViewMapper(SsrAnalysisFailurePresenter failurePresenter, Clock clock) {
		this.failurePresenter = failurePresenter;
		this.clock = clock;
	}

	public InfluencerDirectoryViewModel directory(XStockInfluencerRecommendationsResource resource) {
		return new InfluencerDirectoryViewModel(
			resource.message(), resource.accounts().stream().map(this::profile).toList()
		);
	}

	public InfluencerProfileViewModel profile(XStockInfluencerRecommendationResource resource) {
		var live = "LIVE_X".equals(resource.sourceType());
		return new InfluencerProfileViewModel(
			resource.profileId(), resource.handle(), resource.displayName(), resource.reason(),
			resource.investmentStyle(), live, live ? "실제 계정" : "Fixture",
			resource.avatarInitials(), resource.avatarColor()
		);
	}

	public InfluencerProfilePageViewModel page(
		XStockInfluencerRecommendationResource profile,
		RecentAnalysisViewModel analysis,
		boolean liveEnabled,
		String setupGuidance,
		String executionToken,
		String disclaimer
	) {
		return new InfluencerProfilePageViewModel(
			profile(profile), analysis, liveEnabled, setupGuidance, executionToken, disclaimer
		);
	}

	public RecentAnalysisViewModel fixture(String profileId, RecentMentionedCompaniesResource resource) {
		return analysis(
			resource,
			"fixture:" + profileId + ":v1",
			null,
			null,
			true,
			true,
			false,
			null,
			false
		);
	}

	public RecentAnalysisViewModel live(RecentXAnalysisDetailResource detail) {
		var terminal = isTerminal(detail.status());
		var budgetExceeded = !terminal && exceededPollingBudget(detail.createdAt());
		var failure = terminal && !"COMPLETED".equals(detail.status())
			? failurePresenter.present(detail.outcomeCode())
			: null;
		return analysis(
			detail.result(), detail.runId(), detail.runId(), displayTime(detail.createdAt()),
			false, terminal, !terminal && !budgetExceeded,
			failure == null ? null : failure.guidance(),
			failure != null && failure.retryAllowed()
		);
	}

	private RecentAnalysisViewModel analysis(
		RecentMentionedCompaniesResource resource,
		String runReference,
		String runId,
		String createdAt,
		boolean fixture,
		boolean terminal,
		boolean automaticPolling,
		String recoveryGuidance,
		boolean retryAllowed
	) {
		var companies = resource == null ? List.<RecentCompanyResource>of() : resource.companies();
		var status = resource == null ? "CREATED" : resource.status();
		var message = resource == null ? "분석 실행을 기다리고 있습니다." : resource.message();
		var experiment = resource == null
			? new ExperimentViewModel(0, "0", "0", 2, 1, 0, false, false, List.of())
			: new ExperimentViewModel(
				resource.analyzedPostCount(), callCount(resource.xApiRequestsThisCall()),
				callCount(resource.llmCallsThisCall()), resource.xApiRequestBudget(), resource.llmCallBudget(),
				resource.durationMs(), resource.cacheHit(), fixture, resource.warnings()
			);
		return new RecentAnalysisViewModel(
			status,
			statusLabel(status),
			message,
			terminal,
			automaticPolling,
			!terminal && !automaticPolling && runId != null,
			runId,
			runReference,
			createdAt,
			sections(companies),
			experiment,
			recoveryGuidance,
			retryAllowed
		);
	}

	private List<SentimentSectionViewModel> sections(List<RecentCompanyResource> companies) {
		return SENTIMENTS.stream().map(definition -> new SentimentSectionViewModel(
			definition.code(), definition.label(), definition.emptyMessage(),
			companies.stream()
				.filter(company -> definition.code().equals(company.overallSentiment()))
				.map(this::company)
				.toList()
		)).toList();
	}

	private CompanyViewModel company(RecentCompanyResource resource) {
		return new CompanyViewModel(
			resource.mention(),
			resource.positiveCount() + resource.negativeCount() + resource.neutralCount() + resource.uncertainCount(),
			percent(resource.confidence()),
			resource.conflicting(),
			resource.evidence().stream().map(this::evidence).toList()
		);
	}

	private EvidenceViewModel evidence(RecentCompanyEvidenceResource resource) {
		var external = isAllowedXLink(resource.sourceUrl());
		return new EvidenceViewModel(
			resource.postId(), displayTime(resource.publishedAt()), resource.excerpt(), resource.rationale(),
			resource.sentiment(), percent(resource.confidence()), external,
			external ? resource.sourceUrl().toString() : null,
			external ? "X 원문 열기" : "Pinbabel Fixture 원문"
		);
	}

	private boolean isAllowedXLink(URI uri) {
		return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && "x.com".equalsIgnoreCase(uri.getHost());
	}

	private boolean exceededPollingBudget(Instant createdAt) {
		return createdAt != null && Duration.between(createdAt, clock.instant()).compareTo(AUTOMATIC_POLLING_BUDGET) >= 0;
	}

	private boolean isTerminal(String status) {
		return "COMPLETED".equals(status) || "FAILED".equals(status) || "REJECTED".equals(status);
	}

	private String statusLabel(String status) {
		return switch (status) {
			case "CREATED" -> "접수됨";
			case "RUNNING" -> "분석 중";
			case "COMPLETED" -> "완료";
			case "FAILED" -> "실패";
			case "REJECTED" -> "접수 거절";
			default -> "상태 확인 필요";
		};
	}

	private int percent(double score) {
		return (int) Math.round(score * 100);
	}

	private String callCount(Integer count) {
		return count == null ? "알 수 없음" : count.toString();
	}

	private String displayTime(Instant instant) {
		return instant == null ? "-" : instant.toString();
	}

	private record SentimentDefinition(String code, String label, String emptyMessage) {
	}
}
