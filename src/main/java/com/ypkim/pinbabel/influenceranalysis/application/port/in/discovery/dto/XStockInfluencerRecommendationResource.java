package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

public record XStockInfluencerRecommendationResource(
	String platform,
	String handle,
	String displayName,
	String reason,
	String selectionBasis
) {
}
