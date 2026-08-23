package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationCaseResult;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationScore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.EvaluationRunStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EvaluationRunPersistenceAdapterTest {

	@Autowired
	private EvaluationRunStore store;

	@Test
	void roundTripsCaseResultsAndReturnsDeterministicLatestRuns() {
		var baseline = Instant.parse("2026-08-24T01:00:00Z");
		for (var index = 1; index <= 22; index++) {
			store.save(run(index, baseline.plusSeconds(index / 2)));
		}

		var latest = store.findLatest(20);
		var detail = store.findById(id(22)).orElseThrow();

		assertThat(latest).hasSize(20);
		assertThat(latest.getFirst().id()).isEqualTo(id(22));
		assertThat(latest.getLast().id()).isEqualTo(id(3));
		assertThat(detail.caseResults()).singleElement().satisfies(result -> {
			assertThat(result.caseId()).isEqualTo("case-22");
			assertThat(result.mismatches()).containsExactly("SENTIMENT:NASDAQ:NVDA");
		});
		assertThat(detail.score().instrumentF1()).isEqualTo(1.0);
	}

	private static EvaluationRun run(int suffix, Instant createdAt) {
		var result = new EvaluationCaseResult(
			"case-" + suffix, "analysis-" + suffix, "COMPLETED",
			1, 1, 1, 0, 0, 0, 1, 1, false, List.of("SENTIMENT:NASDAQ:NVDA")
		);
		return new EvaluationRun(
			id(suffix), "dataset", 1, createdAt, createdAt, createdAt.plusSeconds(1),
			List.of(result), EvaluationScore.summarize(List.of(result))
		);
	}

	private static EvaluationRunId id(int suffix) {
		return new EvaluationRunId("0198d1bb-99e0-7000-8000-%012d".formatted(suffix));
	}
}
