package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

public record RecentXPostBatch(
	XAccountHandle account,
	CollectedPosts posts,
	int xApiRequestCount
) {
}
