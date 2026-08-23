package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.AnalysisPeriod;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentSummary;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.ExpectedInstrument;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenEvaluationCase;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenDatasetEvaluatorTest {

	private final GoldenDatasetEvaluator evaluator = new GoldenDatasetEvaluator();

	@Test
	void scoresExactInstrumentSentimentAndEvidenceMatch() {
		var result = evaluator.evaluate(goldenCase(), "run-1", "COMPLETED", report(
			summary("NASDAQ:NVDA", Sentiment.POSITIVE, "post-1", "post-2"),
			summary("NASDAQ:TSLA", Sentiment.NEGATIVE, "post-3")
		));

		assertThat(result.exactMatch()).isTrue();
		assertThat(result.truePositiveCount()).isEqualTo(2);
		assertThat(result.correctSentimentCount()).isEqualTo(2);
		assertThat(result.matchedEvidenceCount()).isEqualTo(3);
		assertThat(result.mismatches()).isEmpty();
	}

	@Test
	void distinguishesDetectionSentimentAndEvidenceFailures() {
		var result = evaluator.evaluate(goldenCase(), "run-2", "COMPLETED", report(
			summary("NASDAQ:NVDA", Sentiment.NEGATIVE, "post-1"),
			summary("NASDAQ:AAPL", Sentiment.POSITIVE, "post-9")
		));

		assertThat(result.exactMatch()).isFalse();
		assertThat(result.truePositiveCount()).isEqualTo(1);
		assertThat(result.falsePositiveCount()).isEqualTo(1);
		assertThat(result.falseNegativeCount()).isEqualTo(1);
		assertThat(result.correctSentimentCount()).isZero();
		assertThat(result.matchedEvidenceCount()).isEqualTo(1);
		assertThat(result.mismatches()).contains(
			"SENTIMENT:NASDAQ:NVDA", "EVIDENCE:NASDAQ:NVDA:post-2",
			"MISSING_INSTRUMENT:NASDAQ:TSLA", "UNEXPECTED_INSTRUMENT:NASDAQ:AAPL"
		);
	}

	@Test
	void failedAnalysisProducesZeroScoresAndNeverExactMatches() {
		var result = evaluator.evaluate(goldenCase(), null, "FAILED", null);

		assertThat(result.analysisCompleted()).isFalse();
		assertThat(result.truePositiveCount()).isZero();
		assertThat(result.falseNegativeCount()).isEqualTo(2);
		assertThat(result.exactMatch()).isFalse();
	}

	private static GoldenEvaluationCase goldenCase() {
		return new GoldenEvaluationCase("case-1", "analyze", List.of(
			new ExpectedInstrument("NASDAQ:NVDA", Sentiment.POSITIVE, List.of("post-1", "post-2")),
			new ExpectedInstrument("NASDAQ:TSLA", Sentiment.NEGATIVE, List.of("post-3"))
		));
	}

	private static InstrumentSummary summary(String id, Sentiment sentiment, String... evidence) {
		return new InstrumentSummary(id, id.substring(id.indexOf(':') + 1), id, sentiment,
			0, 0, 0, 0, false, List.of(evidence));
	}

	private static InfluencerAnalysisReport report(InstrumentSummary... summaries) {
		return new InfluencerAnalysisReport(
			"fixture-social", "voice",
			new AnalysisPeriod(Instant.EPOCH, Instant.EPOCH.plusSeconds(1), ZoneOffset.UTC),
			List.of(summaries), List.of(), List.of(), "Not investment advice"
		);
	}
}
