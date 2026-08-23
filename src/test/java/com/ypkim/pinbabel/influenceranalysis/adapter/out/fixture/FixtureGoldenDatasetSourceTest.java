package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

class FixtureGoldenDatasetSourceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void loadsVersionedBoundedGoldenDataset() {
		var dataset = new FixtureGoldenDatasetSource(objectMapper).load();

		assertThat(dataset.datasetId()).isEqualTo("pinbabel-fixture-sentiment");
		assertThat(dataset.version()).isEqualTo(1);
		assertThat(dataset.cases()).singleElement().satisfies(evaluationCase ->
			assertThat(evaluationCase.expectedInstruments()).hasSize(3));
	}

	@Test
	void malformedDatasetIsTranslatedWithSourceInternalCode() {
		var source = new FixtureGoldenDatasetSource(
			objectMapper,
			new ByteArrayResource("{} trailing".getBytes(StandardCharsets.UTF_8))
		);

		assertThatThrownBy(source::load)
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode()).isEqualTo(
					InfluencerAnalysisInternalCode.GOLDEN_DATASET_READ_FAILED
				));
	}
}
