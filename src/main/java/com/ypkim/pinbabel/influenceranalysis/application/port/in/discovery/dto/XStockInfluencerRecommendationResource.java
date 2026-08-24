package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

public record XStockInfluencerRecommendationResource(
	String profileId,
	String platform,
	String handle,
	String displayName,
	String reason,
	String selectionBasis,
	String investmentStyle,
	String sourceType,
	String avatarInitials,
	String avatarColor
) {
}
