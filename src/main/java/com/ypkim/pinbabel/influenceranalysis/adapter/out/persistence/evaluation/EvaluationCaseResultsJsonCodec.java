package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationCaseResult;
import java.util.Arrays;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@SecondaryAdapter
class EvaluationCaseResultsJsonCodec {

	static final int SCHEMA_VERSION = 1;
	private final ObjectMapper objectMapper;

	EvaluationCaseResultsJsonCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String serialize(List<EvaluationCaseResult> results) {
		try {
			return objectMapper.writeValueAsString(results);
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.EVALUATION_RESULT_SERIALIZATION_FAILED,
				"Evaluation result serialization failed",
				exception
			);
		}
	}

	List<EvaluationCaseResult> deserialize(Integer schemaVersion, String json) {
		if (!Integer.valueOf(SCHEMA_VERSION).equals(schemaVersion)) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.EVALUATION_RESULT_SCHEMA_UNSUPPORTED,
				"Unsupported evaluation result schema version"
			);
		}
		try {
			return Arrays.asList(objectMapper.readValue(json, EvaluationCaseResult[].class));
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.EVALUATION_RESULT_DESERIALIZATION_FAILED,
				"Evaluation result deserialization failed",
				exception
			);
		}
	}
}
