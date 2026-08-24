package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyEvidence;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanyMentionAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentCompanySummary;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.Service;

@Service
public final class RecentCompanyAnalysisService {
	private static final int MAXIMUM_EXCERPT_CHARACTERS = 280;

	public Result summarize(
		CollectedPosts posts,
		RecentCompanyMentionAssessments assessments,
		Map<String, String> analyzedTextByPostId
	) {
		var warnings = new ArrayList<String>();
		var assessmentsByMention = new LinkedHashMap<MentionKey, AssessmentAccumulator>();
		var postsById = posts.posts().stream()
			.collect(Collectors.toUnmodifiableMap(CollectedPost::postId, post -> post));

		for (var assessment : assessments.assessments()) {
			if (!postsById.containsKey(assessment.postId())) {
				warnings.add("RECENT_MENTION_POST_NOT_FOUND");
				continue;
			}
			var analyzedText = analyzedTextByPostId.get(assessment.postId());
			if (analyzedText == null || !analyzedText.contains(assessment.mention())) {
				warnings.add("RECENT_MENTION_NOT_PRESENT_IN_ANALYZED_SOURCE");
				continue;
			}
			var accumulator = assessmentsByMention.computeIfAbsent(
				new MentionKey(assessment.postId(), assessment.mention()),
				ignored -> new AssessmentAccumulator()
			);
			var acceptance = accumulator.accept(assessment);
			if (acceptance == AssessmentAcceptance.DUPLICATE) {
				warnings.add("RECENT_DUPLICATE_MENTION_IGNORED");
			} else if (acceptance == AssessmentAcceptance.CONFLICT) {
				warnings.add("RECENT_CONFLICTING_DUPLICATE_MENTION");
			}
		}

		var accumulators = new LinkedHashMap<String, SummaryAccumulator>();
		for (var entry : assessmentsByMention.entrySet()) {
			var key = entry.getKey();
			var post = postsById.get(key.postId());
			var evidence = entry.getValue().toEvidence(
				post,
				excerpt(analyzedTextByPostId.get(key.postId()), key.mention())
			);
			accumulators.computeIfAbsent(key.mention(), SummaryAccumulator::new)
				.add(evidence);
		}

		var summaries = accumulators.values().stream()
			.map(SummaryAccumulator::toSummary)
			.toList();
		return new Result(summaries, warnings.stream().distinct().toList());
	}

	private record MentionKey(String postId, String mention) {
	}

	private String excerpt(String text, String mention) {
		var mentionStart = text.indexOf(mention);
		var excerptCharacters = Math.max(MAXIMUM_EXCERPT_CHARACTERS, mention.length());
		var availableContext = excerptCharacters - mention.length();
		var start = Math.max(0, mentionStart - availableContext / 2);
		var end = Math.min(text.length(), start + excerptCharacters);
		start = Math.max(0, end - excerptCharacters);
		if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
			start++;
		}
		if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(start, end);
	}

	public record Result(List<RecentCompanySummary> companies, List<String> warnings) {

		public Result {
			companies = List.copyOf(companies);
			warnings = List.copyOf(warnings);
		}
	}

	private static final class SummaryAccumulator {

		private final String mention;
		private final Map<Sentiment, Integer> counts = new EnumMap<>(Sentiment.class);
		private final List<RecentCompanyEvidence> evidence = new ArrayList<>();

		private SummaryAccumulator(String mention) {
			this.mention = mention;
		}

		private void add(RecentCompanyEvidence item) {
			counts.merge(item.sentiment(), 1, Integer::sum);
			evidence.add(item);
		}

		private RecentCompanySummary toSummary() {
			var positive = count(Sentiment.POSITIVE);
			var negative = count(Sentiment.NEGATIVE);
			var neutral = count(Sentiment.NEUTRAL);
			var uncertain = count(Sentiment.UNCERTAIN);
			return new RecentCompanySummary(
				mention,
				overallSentiment(positive, negative, neutral, uncertain),
				positive,
				negative,
				neutral,
				uncertain,
				positive > 0 && negative > 0,
				List.copyOf(evidence)
			);
		}

		private int count(Sentiment sentiment) {
			return counts.getOrDefault(sentiment, 0);
		}

		private Sentiment overallSentiment(int positive, int negative, int neutral, int uncertain) {
			if (positive > negative && positive >= neutral && positive >= uncertain) {
				return Sentiment.POSITIVE;
			}
			if (negative > positive && negative >= neutral && negative >= uncertain) {
				return Sentiment.NEGATIVE;
			}
			if (neutral > 0 && neutral >= uncertain) {
				return Sentiment.NEUTRAL;
			}
			return Sentiment.UNCERTAIN;
		}
	}

	private enum AssessmentAcceptance {
		ACCEPTED,
		DUPLICATE,
		CONFLICT
	}

	private static final class AssessmentAccumulator {

		private final Map<Sentiment, RecentCompanyMentionAssessment> bySentiment =
			new EnumMap<>(Sentiment.class);

		private AssessmentAcceptance accept(RecentCompanyMentionAssessment assessment) {
			var existing = bySentiment.get(assessment.sentiment());
			if (existing != null) {
				if (assessment.confidence() > existing.confidence()) {
					bySentiment.put(assessment.sentiment(), assessment);
				}
				return AssessmentAcceptance.DUPLICATE;
			}
			bySentiment.put(assessment.sentiment(), assessment);
			return bySentiment.size() > 1
				? AssessmentAcceptance.CONFLICT
				: AssessmentAcceptance.ACCEPTED;
		}

		private RecentCompanyEvidence toEvidence(CollectedPost post, String excerpt) {
			if (bySentiment.size() == 1) {
				var assessment = bySentiment.values().iterator().next();
				return evidence(post, excerpt, assessment.sentiment(), assessment.rationale(), assessment.confidence());
			}
			var rationale = bySentiment.values().stream()
				.map(assessment -> assessment.sentiment() + " (" + assessment.rationale() + ")")
				.collect(Collectors.joining("; ", "Conflicting assessments: ", ""));
			var confidence = bySentiment.values().stream()
				.mapToDouble(RecentCompanyMentionAssessment::confidence)
				.min()
				.orElse(0.0);
			return evidence(post, excerpt, Sentiment.UNCERTAIN, rationale, confidence);
		}

		private RecentCompanyEvidence evidence(
			CollectedPost post,
			String excerpt,
			Sentiment sentiment,
			String rationale,
			double confidence
		) {
			return new RecentCompanyEvidence(
				post.postId(),
				post.publishedAt(),
				post.url(),
				excerpt,
				sentiment,
				rationale,
				confidence
			);
		}
	}
}
