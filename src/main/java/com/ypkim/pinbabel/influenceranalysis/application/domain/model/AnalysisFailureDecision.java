package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AnalysisFailureDecision(String outcomeCode, String message) {

	public AnalysisFailureDecision {
		if (outcomeCode == null || outcomeCode.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_FAILURE_OUTCOME_CODE_REQUIRED,
				"Analysis failure outcome code is required"
			);
		}
		if (message == null || message.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_FAILURE_MESSAGE_REQUIRED,
				"Analysis failure message is required"
			);
		}
	}
}
