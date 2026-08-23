package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

class FixtureInstrumentCatalogTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void searchesByTickerCompanyNameAndAliasWithinMarketScope() {
		var catalog = new FixtureInstrumentCatalog(objectMapper);

		assertThat(catalog.search("nvda", Set.of("NASDAQ"), 10))
			.extracting(instrument -> instrument.instrumentId())
			.containsExactly("NASDAQ:NVDA");
		assertThat(catalog.search("nvidia", Set.of("NASDAQ"), 10))
			.extracting(instrument -> instrument.ticker())
			.containsExactly("NVDA");
		assertThat(catalog.search("마이크로소프트", Set.of("NASDAQ"), 10))
			.extracting(instrument -> instrument.ticker())
			.containsExactly("MSFT");
		assertThat(catalog.search("tesla", Set.of("NYSE"), 10)).isEmpty();
	}

	@Test
	void lookupUsesCanonicalInstrumentIdAndSearchHonorsLimit() {
		var catalog = new FixtureInstrumentCatalog(objectMapper);

		assertThat(catalog.findById("NASDAQ:NVDA"))
			.get()
			.extracting(instrument -> instrument.displayName())
			.isEqualTo("NVIDIA Corporation");
		assertThat(catalog.search("a", Set.of(), 2)).hasSize(2);
	}

	@Test
	void malformedInstrumentFixtureIsTranslatedWithSourceInternalCode() {
		var resource = new ByteArrayResource("[] trailing".getBytes(StandardCharsets.UTF_8));
		var catalog = new FixtureInstrumentCatalog(objectMapper, resource);

		assertThatThrownBy(() -> catalog.search("nvda", Set.of(), 10))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode())
					.isEqualTo(InfluencerAnalysisInternalCode.INSTRUMENT_FIXTURE_READ_FAILED));
	}
}
