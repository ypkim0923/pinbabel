package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.List;
import java.util.Locale;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record InstrumentReference(
	@Identity String instrumentId,
	String ticker,
	String exchange,
	String displayName,
	List<String> aliases
) {

	public InstrumentReference {
		if (instrumentId == null || instrumentId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.INSTRUMENT_ID_REQUIRED,
				"Instrument identifier is required"
			);
		}
		if (ticker == null || ticker.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.TICKER_REQUIRED,
				"Instrument ticker is required"
			);
		}
		if (exchange == null || exchange.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.EXCHANGE_REQUIRED,
				"Instrument exchange is required"
			);
		}
		if (displayName == null || displayName.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.DISPLAY_NAME_REQUIRED,
				"Instrument display name is required"
			);
		}
		if (aliases == null || aliases.stream().anyMatch(alias -> alias == null || alias.isBlank())) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ALIASES_REQUIRED,
				"Instrument aliases are required"
			);
		}
		ticker = ticker.trim().toUpperCase(Locale.ROOT);
		exchange = exchange.trim().toUpperCase(Locale.ROOT);
		aliases = List.copyOf(aliases);
	}
}
