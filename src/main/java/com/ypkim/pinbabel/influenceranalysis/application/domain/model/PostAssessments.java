package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PostAssessments(List<PostAssessment> assessments) {

	public PostAssessments {
		if (assessments == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENTS_REQUIRED,
				"Post assessments are required"
			);
		}
		if (assessments.stream().anyMatch(Objects::isNull)) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ASSESSMENT_ITEM_REQUIRED,
				"Post assessments cannot contain null items"
			);
		}
		assessments = List.copyOf(assessments);
	}
}
