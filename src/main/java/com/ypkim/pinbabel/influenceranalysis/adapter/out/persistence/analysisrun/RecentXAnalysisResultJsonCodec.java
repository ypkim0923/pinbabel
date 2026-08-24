package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredRecentXAnalysisResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@Component
@SecondaryAdapter
class RecentXAnalysisResultJsonCodec {

	static final int SCHEMA_VERSION = 1;
	private final ObjectMapper objectMapper;

	RecentXAnalysisResultJsonCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String serialize(StoredRecentXAnalysisResult result) {
		try {
			return objectMapper.writeValueAsString(result);
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_RESULT_SERIALIZATION_FAILED,
				"Recent X analysis result serialization failed", exception
			);
		}
	}

	StoredRecentXAnalysisResult deserialize(int schemaVersion, String json) {
		if (schemaVersion != SCHEMA_VERSION) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_RESULT_SCHEMA_UNSUPPORTED,
				"Unsupported recent X analysis result schema version"
			);
		}
		try {
			return objectMapper.readValue(json, StoredRecentXAnalysisResult.class);
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_RESULT_DESERIALIZATION_FAILED,
				"Recent X analysis result deserialization failed", exception
			);
		}
	}
}
