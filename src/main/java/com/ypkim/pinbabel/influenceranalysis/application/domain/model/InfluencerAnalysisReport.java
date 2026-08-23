package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import java.util.List;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record InfluencerAnalysisReport(
	String platform,
	String influencerId,
	AnalysisPeriod period,
	List<InstrumentSummary> instrumentSummaries,
	List<AssessmentEvidence> evidence,
	List<String> warnings,
	String disclaimer
) {

	public InfluencerAnalysisReport {
		instrumentSummaries = List.copyOf(instrumentSummaries);
		evidence = List.copyOf(evidence);
		warnings = List.copyOf(warnings);
	}

	@Identity
	public String analysisIdentity() {
		return "%s:%s:%s:%s".formatted(
			platform,
			influencerId,
			period.startInclusive(),
			period.endExclusive()
		);
	}
}
