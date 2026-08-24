package com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record InfluencerDirectoryViewModel(
	String message,
	List<InfluencerProfileViewModel> profiles
) {
	public InfluencerDirectoryViewModel {
		profiles = List.copyOf(profiles);
	}
}
