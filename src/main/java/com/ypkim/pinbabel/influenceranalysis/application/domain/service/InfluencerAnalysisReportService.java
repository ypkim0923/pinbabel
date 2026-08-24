package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AssessmentEvidence;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentReference;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentSummary;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.Service;

@Service
public final class InfluencerAnalysisReportService {

	public static final int MAX_EVIDENCE_EXCERPT_LENGTH = 500;

	private static final String FIXTURE_DISCLAIMER =
		"This report summarizes fixture posts for testing and is not investment advice.";
	private static final String PUBLIC_SNS_DISCLAIMER =
		"This report automatically summarizes collected public SNS posts and is not investment advice.";
	private static final String FIXTURE_SCOPE_WARNING =
		"ANALYSIS_LIMITED_TO_COLLECTED_FIXTURE_POSTS";
	private static final String PUBLIC_SNS_SCOPE_WARNING =
		"ANALYSIS_LIMITED_TO_COLLECTED_POSTS";

	public InfluencerAnalysisReport buildReport(
		InfluencerAnalysisRequest request,
		CollectedPosts posts,
		PostAssessments postAssessments,
		Map<String, InstrumentReference> instrumentsById
	) {
		var postsById = new LinkedHashMap<String, CollectedPost>();
		posts.posts().forEach(post -> postsById.put(post.postId(), post));
		var evidence = new ArrayList<AssessmentEvidence>();
		var warnings = new ArrayList<String>();
		warnings.add(scopeWarning(request));
		warnings.addAll(posts.warnings());
		var summaries = new LinkedHashMap<String, SummaryAccumulator>();

		for (var assessment : postAssessments.assessments()) {
			var post = requireWorkspacePost(assessment, postsById);
			if (assessment.instrumentId() == null) {
				evidence.add(toEvidence(post, assessment, null));
				warnings.add("UNCERTAIN assessment for post %s has no canonical instrument"
					.formatted(post.postId()));
				continue;
			}

			var instrument = requireCanonicalInstrument(request, assessment, instrumentsById);
			evidence.add(toEvidence(post, assessment, instrument));
			summaries.computeIfAbsent(
				instrument.instrumentId(),
				ignored -> new SummaryAccumulator(instrument)
			).add(assessment);
			if (assessment.sentiment() == Sentiment.UNCERTAIN) {
				warnings.add("Assessment for post %s and instrument %s remains UNCERTAIN"
					.formatted(post.postId(), instrument.ticker()));
			}
		}
		var assessedPostIds = postAssessments.assessments().stream()
			.map(PostAssessment::postId)
			.collect(Collectors.toUnmodifiableSet());
		var unassessedPostIds = posts.posts().stream()
			.map(CollectedPost::postId)
			.filter(postId -> !assessedPostIds.contains(postId))
			.toList();
		if (!unassessedPostIds.isEmpty()) {
			warnings.add("POSTS_WITHOUT_ASSESSMENTS:" + String.join(",", unassessedPostIds));
		}

		var instrumentSummaries = summaries.values().stream()
			.map(SummaryAccumulator::toSummary)
			.toList();
		instrumentSummaries.stream()
			.filter(InstrumentSummary::conflicting)
			.forEach(summary -> warnings.add(
				"Instrument %s has conflicting positive and negative evidence".formatted(summary.ticker())
			));

		return report(request, instrumentSummaries, evidence, warnings);
	}

	public InfluencerAnalysisReport buildEmptyReport(
		InfluencerAnalysisRequest request,
		CollectedPosts posts
	) {
		var warnings = new ArrayList<>(posts.warnings());
		warnings.add("NO_POSTS");
		return report(request, List.of(), List.of(), warnings);
	}

	private CollectedPost requireWorkspacePost(
		PostAssessment assessment,
		Map<String, CollectedPost> postsById
	) {
		var post = postsById.get(assessment.postId());
		if (post == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_POST_NOT_FOUND,
				"Assessment references a post outside the current workspace"
			);
		}
		return post;
	}

	private InstrumentReference requireCanonicalInstrument(
		InfluencerAnalysisRequest request,
		PostAssessment assessment,
		Map<String, InstrumentReference> instrumentsById
	) {
		var instrument = instrumentsById.get(assessment.instrumentId());
		if (instrument == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_INSTRUMENT_NOT_FOUND,
				"Assessment references an unknown canonical instrument"
			);
		}
		if (!instrument.ticker().equalsIgnoreCase(assessment.ticker())) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_TICKER_MISMATCH,
				"Assessment ticker does not match the canonical instrument"
			);
		}
		if (!request.marketCodes().isEmpty() && !request.marketCodes().contains(instrument.exchange())) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_MARKET_NOT_ALLOWED,
				"Assessment instrument is outside the requested market scope"
			);
		}
		return instrument;
	}

	private AssessmentEvidence toEvidence(
		CollectedPost post,
		PostAssessment assessment,
		InstrumentReference instrument
	) {
		return new AssessmentEvidence(
			post.postId(),
			post.platform(),
			post.authorId(),
			post.publishedAt(),
			post.url().toString(),
			post.source(),
			instrument == null ? null : instrument.instrumentId(),
			instrument == null ? null : instrument.ticker(),
			assessment.sentiment(),
			excerpt(post.text()),
			assessment.rationale()
		);
	}

	private String excerpt(String postText) {
		return postText.length() <= MAX_EVIDENCE_EXCERPT_LENGTH
			? postText
			: postText.substring(0, MAX_EVIDENCE_EXCERPT_LENGTH);
	}

	private InfluencerAnalysisReport report(
		InfluencerAnalysisRequest request,
		List<InstrumentSummary> summaries,
		List<AssessmentEvidence> evidence,
		List<String> warnings
	) {
		return new InfluencerAnalysisReport(
			request.platform(),
			request.influencerId(),
			request.period(),
			summaries,
			evidence,
			warnings,
			disclaimer(request)
		);
	}

	private String scopeWarning(InfluencerAnalysisRequest request) {
		return "fixture-social".equals(request.platform())
			? FIXTURE_SCOPE_WARNING
			: PUBLIC_SNS_SCOPE_WARNING;
	}

	private String disclaimer(InfluencerAnalysisRequest request) {
		return "fixture-social".equals(request.platform())
			? FIXTURE_DISCLAIMER
			: PUBLIC_SNS_DISCLAIMER;
	}

	private static final class SummaryAccumulator {

		private final InstrumentReference instrument;
		private final List<String> evidencePostIds = new ArrayList<>();
		private int positiveCount;
		private int negativeCount;
		private int neutralCount;
		private int uncertainCount;

		private SummaryAccumulator(InstrumentReference instrument) {
			this.instrument = instrument;
		}

		private void add(PostAssessment assessment) {
			evidencePostIds.add(assessment.postId());
			switch (assessment.sentiment()) {
				case POSITIVE -> positiveCount++;
				case NEGATIVE -> negativeCount++;
				case NEUTRAL -> neutralCount++;
				case UNCERTAIN -> uncertainCount++;
			}
		}

		private InstrumentSummary toSummary() {
			return new InstrumentSummary(
				instrument.instrumentId(),
				instrument.ticker().toUpperCase(Locale.ROOT),
				instrument.displayName(),
				overallSentiment(),
				positiveCount,
				negativeCount,
				neutralCount,
				uncertainCount,
				positiveCount > 0 && negativeCount > 0,
				evidencePostIds
			);
		}

		private Sentiment overallSentiment() {
			if (positiveCount > 0 && negativeCount > 0) {
				return Sentiment.UNCERTAIN;
			}
			if (positiveCount > 0) {
				return Sentiment.POSITIVE;
			}
			if (negativeCount > 0) {
				return Sentiment.NEGATIVE;
			}
			if (uncertainCount > 0) {
				return Sentiment.UNCERTAIN;
			}
			return Sentiment.NEUTRAL;
		}
	}
}
