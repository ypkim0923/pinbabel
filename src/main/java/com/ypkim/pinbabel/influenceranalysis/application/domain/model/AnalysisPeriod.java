package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.time.Instant;
import java.time.ZoneId;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisPeriod(
	Instant startInclusive,
	Instant endExclusive,
	ZoneId timezone
) {

	public AnalysisPeriod {
		if (startInclusive == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.PERIOD_START_REQUIRED,
				"Analysis period start is required"
			);
		}
		if (endExclusive == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.PERIOD_END_REQUIRED,
				"Analysis period end is required"
			);
		}
		if (timezone == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.PERIOD_TIMEZONE_REQUIRED,
				"Analysis period timezone is required"
			);
		}
		if (!startInclusive.isBefore(endExclusive)) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.INVALID_PERIOD_ORDER,
				"Analysis period start must be before end"
			);
		}
	}

	public boolean contains(Instant instant) {
		return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
	}
}
