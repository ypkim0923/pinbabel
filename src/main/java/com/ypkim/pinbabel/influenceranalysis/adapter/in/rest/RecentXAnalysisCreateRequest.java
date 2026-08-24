package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record RecentXAnalysisCreateRequest(
	@NotBlank
	@Size(max = 16)
	@Pattern(regexp = "@?[A-Za-z0-9_]{1,15}")
	String account
) {
}
