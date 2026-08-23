package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record CollectedPosts(List<CollectedPost> posts) {

	public static final int MAX_POSTS_PER_RUN = 100;

	public CollectedPosts {
		if (posts == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POSTS_REQUIRED,
				"Collected posts are required"
			);
		}
		if (posts.stream().anyMatch(Objects::isNull)) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_ITEM_REQUIRED,
				"Collected posts cannot contain null items"
			);
		}
		if (posts.size() > MAX_POSTS_PER_RUN) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.TOO_MANY_POSTS,
				"Collected posts exceed the per-run limit"
			);
		}
		posts = List.copyOf(posts);
	}

	public boolean isEmpty() {
		return posts.isEmpty();
	}

	@Identity
	public String collectionIdentity() {
		if (posts.isEmpty()) {
			return "empty";
		}
		return posts.stream()
			.map(CollectedPost::postId)
			.collect(Collectors.joining("|"));
	}
}
