package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto;

import java.util.List;

public record XStockInfluencerRecommendationsResource(
	String message,
	boolean xApiUsed,
	boolean llmUsed,
	List<XStockInfluencerRecommendationResource> accounts
) {

	public XStockInfluencerRecommendationsResource {
		accounts = List.copyOf(accounts);
	}
}
