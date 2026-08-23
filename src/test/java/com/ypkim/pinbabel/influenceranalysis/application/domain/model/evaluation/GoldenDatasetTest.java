package com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenDatasetTest {

	@Test
	void rejectsDuplicateCaseInstrumentAndEvidenceIdentifiers() {
		var expected = new ExpectedInstrument("NASDAQ:NVDA", Sentiment.POSITIVE, List.of("post-1"));
		var evaluationCase = new GoldenEvaluationCase("case-1", "analyze", List.of(expected));

		assertThatThrownBy(() -> new GoldenDataset("dataset", 1, List.of(evaluationCase, evaluationCase)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GoldenEvaluationCase("case-1", "analyze", List.of(expected, expected)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ExpectedInstrument(
			"NASDAQ:NVDA", Sentiment.POSITIVE, List.of("post-1", "post-1")
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
