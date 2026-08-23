package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import org.springframework.stereotype.Component;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import tools.jackson.databind.ObjectMapper;

@Component
@SecondaryAdapter
class AnalysisReportJsonCodec {

	static final int SCHEMA_VERSION = 1;

	private final ObjectMapper objectMapper;

	AnalysisReportJsonCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String serialize(InfluencerAnalysisReport report) {
		try {
			return objectMapper.writeValueAsString(report);
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_REPORT_SERIALIZATION_FAILED,
				"Analysis report serialization failed",
				exception
			);
		}
	}

	InfluencerAnalysisReport deserialize(Integer schemaVersion, String reportJson) {
		if (reportJson == null) {
			return null;
		}
		if (!Integer.valueOf(SCHEMA_VERSION).equals(schemaVersion)) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_REPORT_SCHEMA_UNSUPPORTED,
				"Unsupported analysis report schema version"
			);
		}
		try {
			return objectMapper.readValue(reportJson, InfluencerAnalysisReport.class);
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_REPORT_DESERIALIZATION_FAILED,
				"Analysis report deserialization failed",
				exception
			);
		}
	}
}
