package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RecentCompanyMentionAssessments(List<RecentCompanyMentionAssessment> assessments) {

	public RecentCompanyMentionAssessments {
		if (assessments == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTIONS_REQUIRED,
				"Recent company mention assessments are required"
			);
		}
		if (assessments.stream().anyMatch(Objects::isNull)) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_MENTION_ITEM_REQUIRED,
				"Recent company mention assessments cannot contain null items"
			);
		}
		assessments = List.copyOf(assessments);
	}
}
