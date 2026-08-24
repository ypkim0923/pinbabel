package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.common.ai.model.LlmOptions;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentXCompanyAnalysis;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentXPostBatch;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.RecentCompanyAnalysisService;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.RecentSocialPostSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;

@Agent(description = "Analyze companies mentioned in an X account's ten most recent non-reply, non-repost posts")
@Profile("fixture & x")
public class RecentXStockInfluencerAgent {

	static final Duration LLM_TIMEOUT = Duration.ofSeconds(60);
	static final int MAX_POST_CHARACTERS = 4_000;
	static final int MAX_TOTAL_POST_CHARACTERS = 20_000;
	static final String TRUNCATED_POST_WARNING = "RECENT_POST_TEXT_TRUNCATED_FOR_LLM";

	private final RecentSocialPostSource recentPostSource;
	private final RecentCompanyAnalysisService analysisService = new RecentCompanyAnalysisService();

	public RecentXStockInfluencerAgent(RecentSocialPostSource recentPostSource) {
		this.recentPostSource = recentPostSource;
	}

	@Action(
		description = "Collect at most ten recent X posts while excluding replies and simple reposts",
		post = {"hasRecentOriginalPosts", "hasNoRecentOriginalPosts"},
		readOnly = true,
		actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE
	)
	public RecentXPostBatch collectRecentOriginalPosts(XAccountHandle account) {
		return recentPostSource.findRecentOriginalPosts(account);
	}

	@Condition(name = "hasRecentOriginalPosts")
	public boolean hasRecentOriginalPosts(RecentXPostBatch batch) {
		return !batch.posts().isEmpty();
	}

	@Condition(name = "hasNoRecentOriginalPosts")
	public boolean hasNoRecentOriginalPosts(RecentXPostBatch batch) {
		return batch.posts().isEmpty();
	}

	@Action(
		description = "Extract exact company expressions and sentiment from the bounded recent X post text",
		pre = "hasRecentOriginalPosts",
		readOnly = true,
		actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE
	)
	public RecentCompanyMentionAssessments assessRecentPosts(
		RecentXPostBatch batch,
		OperationContext context
	) {
		var boundedInput = boundedInput(batch);
		return context.ai()
			.withLlm(LlmOptions.withDefaultLlm().withTimeout(LLM_TIMEOUT))
			.rendering("influenceranalysis/analyze-recent-x-companies")
			.createObject(
				RecentCompanyMentionAssessments.class,
				Map.of(
					"account", batch.account().displayHandle(),
					"posts", boundedInput.promptModel()
				)
			);
	}

	@AchievesGoal(description = "Return mentioned, positively assessed, and negatively assessed companies")
	@Action(
		description = "Validate source-grounded mentions and aggregate exact expressions without ticker normalization",
		pre = "hasRecentOriginalPosts",
		readOnly = true
	)
	public RecentXCompanyAnalysis buildRecentCompanyAnalysis(
		RecentXPostBatch batch,
		RecentCompanyMentionAssessments assessments
	) {
		var boundedInput = boundedInput(batch);
		var result = analysisService.summarize(
			batch.posts(),
			assessments,
			boundedInput.textByPostId()
		);
		var warnings = new ArrayList<>(batch.posts().warnings());
		warnings.addAll(result.warnings());
		if (boundedInput.truncated()) {
			warnings.add(TRUNCATED_POST_WARNING);
		}
		return new RecentXCompanyAnalysis(
			batch.account(),
			batch.posts().posts().size(),
			result.companies(),
			warnings.stream().distinct().toList(),
			batch.xApiRequestCount(),
			1
		);
	}

	@AchievesGoal(description = "Return an empty recent-company analysis without an LLM call")
	@Action(
		description = "Return no companies when the recent original-post timeline is empty",
		pre = "hasNoRecentOriginalPosts",
		readOnly = true
	)
	public RecentXCompanyAnalysis buildEmptyRecentCompanyAnalysis(RecentXPostBatch batch) {
		var warnings = new ArrayList<>(batch.posts().warnings());
		warnings.add("NO_RECENT_ORIGINAL_POSTS");
		return new RecentXCompanyAnalysis(
			batch.account(),
			0,
			List.of(),
			warnings,
			batch.xApiRequestCount(),
			0
		);
	}

	private BoundedInput boundedInput(RecentXPostBatch batch) {
		var posts = batch.posts().posts();
		var limits = fairCharacterLimits(posts.stream().map(post -> post.text().length()).toList());
		var model = new ArrayList<Map<String, Object>>();
		var textByPostId = new LinkedHashMap<String, String>();
		var truncated = false;
		for (var index = 0; index < posts.size(); index++) {
			var post = posts.get(index);
			var text = safePrefix(post.text(), limits.get(index));
			truncated |= text.length() < post.text().length();
			var item = new LinkedHashMap<String, Object>();
			item.put("postId", post.postId());
			item.put("publishedAt", post.publishedAt());
			item.put("kind", post.kind());
			item.put("text", text);
			model.add(Map.copyOf(item));
			textByPostId.put(post.postId(), text);
		}
		return new BoundedInput(List.copyOf(model), Map.copyOf(textByPostId), truncated);
	}

	private List<Integer> fairCharacterLimits(List<Integer> lengths) {
		if (lengths.isEmpty()) {
			return List.of();
		}
		var baseShare = MAX_TOTAL_POST_CHARACTERS / lengths.size();
		var limits = new ArrayList<Integer>(lengths.size());
		var allocated = 0;
		for (var length : lengths) {
			var limit = Math.min(length, Math.min(MAX_POST_CHARACTERS, baseShare));
			limits.add(limit);
			allocated += limit;
		}
		var remaining = MAX_TOTAL_POST_CHARACTERS - allocated;
		for (var index = 0; index < lengths.size() && remaining > 0; index++) {
			var available = Math.min(MAX_POST_CHARACTERS, lengths.get(index)) - limits.get(index);
			if (available <= 0) {
				continue;
			}
			var extra = Math.min(available, remaining);
			limits.set(index, limits.get(index) + extra);
			remaining -= extra;
		}
		return List.copyOf(limits);
	}

	private String safePrefix(String text, int maximumCharacters) {
		if (maximumCharacters <= 0) {
			return "";
		}
		if (text.length() <= maximumCharacters) {
			return text;
		}
		var end = maximumCharacters;
		if (Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, end);
	}

	private record BoundedInput(
		List<Map<String, Object>> promptModel,
		Map<String, String> textByPostId,
		boolean truncated
	) {
	}
}
