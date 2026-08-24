package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisScopePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record InfluencerAnalysisCreateRequest(
	@NotNull
	@NotBlank
	@Size(max = AnalysisScopePolicy.MAX_INPUT_LENGTH)
	String instruction
) {
	SubmitInfluencerAnalysisCommand toCommand() {
		return new SubmitInfluencerAnalysisCommand(instruction);
	}
}
