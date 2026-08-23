package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import com.ypkim.pinbabel.influenceranalysis.application.port.out.InstrumentCatalog;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentReference;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("fixture")
@SecondaryAdapter
public class FixtureInstrumentCatalog implements InstrumentCatalog {

	private static final String DEFAULT_FIXTURE = "fixtures/influenceranalysis/instruments.json";
	private static final Comparator<InstrumentReference> INSTRUMENT_ORDER = Comparator
		.comparing(InstrumentReference::instrumentId);

	private final ObjectMapper objectMapper;
	private final Resource fixture;

	@Autowired
	public FixtureInstrumentCatalog(ObjectMapper objectMapper) {
		this(objectMapper, new ClassPathResource(DEFAULT_FIXTURE));
	}

	FixtureInstrumentCatalog(ObjectMapper objectMapper, Resource fixture) {
		this.objectMapper = objectMapper;
		this.fixture = fixture;
	}

	@Override
	public List<InstrumentReference> search(String query, Set<String> marketCodes, int limit) {
		if (query == null || query.isBlank() || limit <= 0) {
			return List.of();
		}
		var normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
		var normalizedMarkets = marketCodes == null
			? Set.<String>of()
			: marketCodes.stream()
				.filter(code -> code != null && !code.isBlank())
				.map(code -> code.trim().toUpperCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());

		return loadAll().stream()
			.filter(instrument -> normalizedMarkets.isEmpty() || normalizedMarkets.contains(instrument.exchange()))
			.filter(instrument -> matches(instrument, normalizedQuery))
			.sorted(INSTRUMENT_ORDER)
			.limit(limit)
			.toList();
	}

	@Override
	public Optional<InstrumentReference> findById(String instrumentId) {
		if (instrumentId == null || instrumentId.isBlank()) {
			return Optional.empty();
		}
		return loadAll().stream()
			.filter(instrument -> instrument.instrumentId().equalsIgnoreCase(instrumentId.trim()))
			.findFirst();
	}

	private List<InstrumentReference> loadAll() {
		try (var input = fixture.getInputStream()) {
			return Arrays.stream(objectMapper.readValue(input, InstrumentFixture[].class))
				.map(item -> new InstrumentReference(
					item.instrumentId(),
					item.ticker(),
					item.exchange(),
					item.displayName(),
					item.aliases()
				))
				.toList();
		} catch (IOException | JacksonException | IllegalArgumentException | InfluencerAnalysisException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.INSTRUMENT_FIXTURE_READ_FAILED,
				"Unable to read the instrument fixture",
				exception
			);
		}
	}

	private boolean matches(InstrumentReference instrument, String query) {
		return instrument.instrumentId().toLowerCase(Locale.ROOT).contains(query)
			|| instrument.ticker().toLowerCase(Locale.ROOT).contains(query)
			|| instrument.displayName().toLowerCase(Locale.ROOT).contains(query)
			|| instrument.aliases().stream()
				.map(alias -> alias.toLowerCase(Locale.ROOT))
				.anyMatch(alias -> alias.contains(query));
	}

	private record InstrumentFixture(
		String instrumentId,
		String ticker,
		String exchange,
		String displayName,
		List<String> aliases
	) {
	}
}
