package com.ypkim.pinbabel.influenceranalysis.adapter.in.web.view;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record InfluencerProfileViewModel(
	String profileId,
	String handle,
	String displayName,
	String description,
	String investmentStyle,
	boolean live,
	String sourceLabel,
	String avatarInitials,
	String avatarColor
) {
}
