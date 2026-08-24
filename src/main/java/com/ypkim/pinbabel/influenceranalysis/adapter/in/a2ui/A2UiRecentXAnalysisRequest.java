package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record A2UiRecentXAnalysisRequest(
	@NotBlank
	@Size(max = 16)
	@Pattern(regexp = "@?[A-Za-z0-9_]{1,15}")
	String account
) {
}
