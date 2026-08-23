package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record InfluencerAnalysisRequest(
	String platform,
	String influencerId,
	AnalysisPeriod period,
	Set<String> marketCodes
) {

	public InfluencerAnalysisRequest {
		if (platform == null || platform.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.PLATFORM_REQUIRED,
				"Platform is required"
			);
		}
		if (influencerId == null || influencerId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.INFLUENCER_REQUIRED,
				"Influencer identifier is required"
			);
		}
		if (period == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.PERIOD_REQUIRED,
				"Analysis period is required"
			);
		}
		if (marketCodes == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.MARKET_CODES_REQUIRED,
				"Market code collection is required"
			);
		}
		platform = platform.trim().toLowerCase(Locale.ROOT);
		influencerId = influencerId.trim();
		marketCodes = marketCodes.stream()
			.filter(code -> code != null && !code.isBlank())
			.map(code -> code.trim().toUpperCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}
}
