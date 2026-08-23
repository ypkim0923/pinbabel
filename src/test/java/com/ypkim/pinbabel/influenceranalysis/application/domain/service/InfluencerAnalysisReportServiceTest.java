package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPost;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.CollectedPosts;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentReference;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostAssessments;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.PostKind;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InfluencerAnalysisReportServiceTest {

	private final InfluencerAnalysisReportService service = new InfluencerAnalysisReportService();

	@Test
	void aggregatesAssessmentsUsingCanonicalDomainFacts() {
		var post = post();
		var instrument = instrument();
		var assessments = new PostAssessments(List.of(
			new PostAssessment(post.postId(), instrument.instrumentId(), "nvda", Sentiment.POSITIVE, "Strong execution")
		));

		var report = service.buildReport(
			request(),
			new CollectedPosts(List.of(post)),
			assessments,
			Map.of(instrument.instrumentId(), instrument)
		);

		assertThat(report.instrumentSummaries()).singleElement().satisfies(summary -> {
			assertThat(summary.ticker()).isEqualTo("NVDA");
			assertThat(summary.overallSentiment()).isEqualTo(Sentiment.POSITIVE);
			assertThat(summary.evidencePostIds()).containsExactly(post.postId());
		});
		assertThat(report.evidence()).singleElement().satisfies(evidence -> {
			assertThat(evidence.postId()).isEqualTo(post.postId());
			assertThat(evidence.source()).isEqualTo("pinbabel-fixture");
		});
	}

	@Test
	void buildsNoPostsReportWithoutAssessments() {
		var report = service.buildEmptyReport(request());

		assertThat(report.instrumentSummaries()).isEmpty();
		assertThat(report.evidence()).isEmpty();
		assertThat(report.warnings()).containsExactly("NO_POSTS");
	}

	@Test
	void boundsPersistableEvidenceExcerpt() {
		var post = post("x".repeat(InfluencerAnalysisReportService.MAX_EVIDENCE_EXCERPT_LENGTH + 10));
		var instrument = instrument();
		var assessments = new PostAssessments(List.of(
			new PostAssessment(post.postId(), instrument.instrumentId(), "NVDA", Sentiment.POSITIVE, "Strong")
		));

		var report = service.buildReport(
			request(), new CollectedPosts(List.of(post)), assessments, Map.of(instrument.instrumentId(), instrument)
		);

		assertThat(report.evidence()).singleElement().satisfies(evidence ->
			assertThat(evidence.excerpt()).hasSize(InfluencerAnalysisReportService.MAX_EVIDENCE_EXCERPT_LENGTH)
		);
	}

	@Test
	void rejectsInstrumentThatIsNotInCanonicalWorkspace() {
		var post = post();
		var assessments = new PostAssessments(List.of(
			new PostAssessment(post.postId(), "NASDAQ:FAKE", "FAKE", Sentiment.POSITIVE, "Invented")
		));

		assertThatThrownBy(() -> service.buildReport(
			request(),
			new CollectedPosts(List.of(post)),
			assessments,
			Map.of()
		))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.ASSESSMENT_INSTRUMENT_NOT_FOUND));
	}

	private static InfluencerAnalysisRequest request() {
		return new InfluencerAnalysisRequest(
			"fixture-social",
			"0007-market-voice",
			new AnalysisPeriod(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-03T00:00:00Z"),
				ZoneOffset.UTC
			),
			Set.of("NASDAQ")
		);
	}

	private static CollectedPost post() {
		return post("NVIDIA is executing well.");
	}

	private static CollectedPost post(String text) {
		return new CollectedPost(
			"post-positive",
			"fixture-social",
			"0007-market-voice",
			Instant.parse("2026-01-01T00:00:00Z"),
			URI.create("https://social.example/posts/post-positive"),
			text,
			"pinbabel-fixture",
			PostKind.ORIGINAL
		);
	}

	private static InstrumentReference instrument() {
		return new InstrumentReference(
			"NASDAQ:NVDA",
			"NVDA",
			"NASDAQ",
			"NVIDIA Corporation",
			List.of("NVIDIA")
		);
	}
}
