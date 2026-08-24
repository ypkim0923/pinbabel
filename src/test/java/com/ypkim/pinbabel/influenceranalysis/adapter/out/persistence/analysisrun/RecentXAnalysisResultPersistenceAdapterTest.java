package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.RecentXAnalysisResultStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredRecentXAnalysisResult;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RecentXAnalysisResultPersistenceAdapterTest {

	@Autowired AnalysisRunStore runStore;
	@Autowired RecentXAnalysisResultStore resultStore;

	@Test
	void storesAndReplacesVersionedRecentResultByRunId() {
		var runId = new AnalysisRunId("0198d1bb-99e0-7000-8000-000000000111");
		runStore.save(AnalysisRun.create(runId, Instant.parse("2026-08-24T00:00:00Z")), null);
		var result = result("CREATED", List.of());
		resultStore.save(runId, result);
		var completed = result("COMPLETED", List.of(new StoredRecentXAnalysisResult.Company(
			"Microsoft", "POSITIVE", 1, 0, 0, 0, false, 0.9,
			List.of(new StoredRecentXAnalysisResult.Evidence(
				"post-1", Instant.parse("2026-08-23T00:00:00Z"), URI.create("https://x.com/u/status/1"),
				"Microsoft is strong", "POSITIVE", "explicit praise", 0.9
			))
		)));

		resultStore.save(runId, completed);

		assertThat(resultStore.findByRunId(runId)).contains(completed);
	}

	private StoredRecentXAnalysisResult result(String status, List<StoredRecentXAnalysisResult.Company> companies) {
		return new StoredRecentXAnalysisResult(
			status, "message", "@account", companies.isEmpty() ? 0 : 1, true, true, false,
			2, 1, 2, 1, 10, companies, List.of(), "not advice"
		);
	}
}
