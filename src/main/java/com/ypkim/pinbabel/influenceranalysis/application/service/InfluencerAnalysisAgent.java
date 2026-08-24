package com.ypkim.pinbabel.influenceranalysis.application.service;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisIntent;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisScopeDecision;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentReference;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.InfluencerAnalysisReportService;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisScopePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.InstrumentCatalog;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.SocialPostSource;
import com.ypkim.pinbabel.influenceranalysis.application.service.tool.AnalysisWorkspaceTools;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;

@Agent(description = "Analyze a named stock influencer's SNS posts for an explicit time period; refuse all other tasks")
@Profile("fixture")
public class InfluencerAnalysisAgent {

	private final SocialPostSource socialPostSource;
	private final InstrumentCatalog instrumentCatalog;
	private final AnalysisScopePolicy scopePolicy;
	private final InfluencerAnalysisReportService reportService =
		new InfluencerAnalysisReportService();

	public InfluencerAnalysisAgent(
		SocialPostSource socialPostSource,
		InstrumentCatalog instrumentCatalog
	) {
		this.socialPostSource = socialPostSource;
		this.instrumentCatalog = instrumentCatalog;
		this.scopePolicy = new AnalysisScopePolicy(socialPostSource.supportedPlatforms());
	}

	@Action(
		description = "Extract only the requested stock influencer post analysis scope from user input",
		readOnly = true
	)
	public AnalysisIntent interpretInput(UserInput userInput, OperationContext context) {
		return context.ai()
			.withDefaultLlm()
			.rendering("influenceranalysis/classify-analysis-intent")
			.createObject(AnalysisIntent.class, Map.of("userInput", userInput.getContent()));
	}

	@Action(
		description = "Apply deterministic Pinbabel scope and input policy",
		post = {"analysisAccepted", "analysisRejected", "analysisIncomplete"},
		readOnly = true
	)
	public AnalysisScopeDecision evaluateScope(UserInput userInput, AnalysisIntent intent) {
		return scopePolicy.evaluate(userInput.getContent(), intent);
	}

	@Condition(name = "analysisAccepted")
	public boolean analysisAccepted(AnalysisScopeDecision decision) {
		return decision.status() == AnalysisScopeDecision.Status.ACCEPTED;
	}

	@Condition(name = "analysisRejected")
	public boolean analysisRejected(AnalysisScopeDecision decision) {
		return decision.status() == AnalysisScopeDecision.Status.REJECTED;
	}

	@Condition(name = "analysisIncomplete")
	public boolean analysisIncomplete(AnalysisScopeDecision decision) {
		return decision.status() == AnalysisScopeDecision.Status.INCOMPLETE;
	}

	@Action(
		description = "Create a validated typed analysis request",
		pre = "analysisAccepted",
		readOnly = true
	)
	public InfluencerAnalysisRequest createAnalysisRequest(AnalysisScopeDecision decision) {
		return decision.request();
	}

	@Action(
		description = "Collect bounded posts for the requested influencer and period",
		post = {"hasPosts", "noPosts"},
		readOnly = true
	)
	public CollectedPosts collectPosts(InfluencerAnalysisRequest request) {
		return socialPostSource.findPosts(request);
	}

	@Condition(name = "hasPosts")
	public boolean hasPosts(CollectedPosts posts) {
		return !posts.isEmpty();
	}

	@Condition(name = "noPosts")
	public boolean noPosts(CollectedPosts posts) {
		return posts.isEmpty();
	}

	@Action(
		description = "Assess collected posts using the request-scoped post and instrument tools",
		pre = "hasPosts",
		readOnly = true
	)
	public PostAssessments assessPosts(
		InfluencerAnalysisRequest request,
		CollectedPosts posts,
		OperationContext context
	) {
		var workspace = new AnalysisWorkspaceTools(posts, request.marketCodes(), instrumentCatalog);
		var model = Map.<String, Object>of(
			"platform", request.platform(),
			"influencerId", request.influencerId(),
			"startInclusive", request.period().startInclusive(),
			"endExclusive", request.period().endExclusive(),
			"marketCodes", request.marketCodes().stream().sorted().toList(),
			"postCount", posts.posts().size(),
			"posts", postMetadata(posts)
		);

		return context.ai()
			.withDefaultLlm()
			.withToolObject(workspace)
			.rendering("influenceranalysis/assess-posts")
			.createObject(PostAssessments.class, model);
	}

	@AchievesGoal(description = "Build a provenance-preserving stock sentiment report")
	@Action(
		description = "Validate structured assessments and aggregate them into the final report",
		pre = "hasPosts",
		readOnly = true
	)
	public InfluencerAnalysisOutcome buildReport(
		InfluencerAnalysisRequest request,
		CollectedPosts posts,
		PostAssessments postAssessments
	) {
		return InfluencerAnalysisOutcome.completed(reportService.buildReport(
			request,
			posts,
			postAssessments,
			canonicalInstruments(postAssessments)
		));
	}

	@AchievesGoal(description = "Return an empty report when the requested period has no posts")
	@Action(
		description = "Build a no-posts report without invoking an LLM",
		pre = "noPosts",
		readOnly = true
	)
	public InfluencerAnalysisOutcome buildEmptyReport(
		InfluencerAnalysisRequest request,
		CollectedPosts posts
	) {
		return InfluencerAnalysisOutcome.completed(reportService.buildEmptyReport(request, posts));
	}

	@AchievesGoal(description = "Refuse requests outside stock influencer post analysis")
	@Action(
		description = "Return a safe domain refusal without using analysis tools",
		pre = "analysisRejected",
		readOnly = true
	)
	public InfluencerAnalysisOutcome refuseRequest(AnalysisScopeDecision decision) {
		return InfluencerAnalysisOutcome.refused(decision);
	}

	@AchievesGoal(description = "Request missing stock influencer analysis fields")
	@Action(
		description = "Explain which required analysis fields are missing without using analysis tools",
		pre = "analysisIncomplete",
		readOnly = true
	)
	public InfluencerAnalysisOutcome requestDetails(AnalysisScopeDecision decision) {
		return InfluencerAnalysisOutcome.refused(decision);
	}

	private List<Map<String, Object>> postMetadata(CollectedPosts posts) {
		return posts.posts().stream()
			.map(post -> Map.<String, Object>of(
				"postId", post.postId(),
				"publishedAt", post.publishedAt(),
				"kind", post.kind(),
				"source", post.source()
			))
			.toList();
	}

	private Map<String, InstrumentReference> canonicalInstruments(PostAssessments postAssessments) {
		var instrumentsById = new LinkedHashMap<String, InstrumentReference>();
		for (var assessment : postAssessments.assessments()) {
			if (assessment.instrumentId() == null || instrumentsById.containsKey(assessment.instrumentId())) {
				continue;
			}
			instrumentCatalog.findById(assessment.instrumentId())
				.ifPresent(instrument -> instrumentsById.put(instrument.instrumentId(), instrument));
		}
		return Map.copyOf(instrumentsById);
	}
}
