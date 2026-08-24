package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunMetricsResource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class A2UiSnapshotRendererTest {

	@Test
	void rendersThreeOrderedV09JsonLinesWithRootComponent() throws Exception {
		var run = new AnalysisRunDetailResource(
			"0198d1bb-99e0-7000-8000-000000000001", "0198d1bb-99e0-7000-8000-000000000002",
			"RUNNING", Instant.parse("2026-08-24T01:00:00Z"), null, null, null, false,
			null, null, null, new AnalysisRunMetricsResource(null, null, null, List.of()), null, List.of()
		);
		var mapper = new ObjectMapper();
		var lines = new A2UiSnapshotRenderer(mapper).render(run).lines().toList();

		assertThat(lines).hasSize(3);
		assertThat(mapper.readTree(lines.get(0)).path("version").asText()).isEqualTo("v0.9");
		assertThat(mapper.readTree(lines.get(0)).has("createSurface")).isTrue();
		assertThat(mapper.readTree(lines.get(1)).path("updateComponents").path("components").get(0).path("id").asText())
			.isEqualTo("root");
		assertThat(mapper.readTree(lines.get(2)).path("updateDataModel").path("value").path("status").asText())
			.isEqualTo("RUNNING");
	}
}
