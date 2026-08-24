package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record CollectedPosts(List<CollectedPost> posts, List<String> warnings) {

	public static final int MAX_POSTS_PER_RUN = 50;

	public CollectedPosts(List<CollectedPost> posts) {
		this(posts, List.of());
	}

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
		if (warnings == null || warnings.stream().anyMatch(warning -> warning == null || warning.isBlank())) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.COLLECTION_WARNINGS_INVALID,
				"Collection warnings must be a non-null list of non-blank values"
			);
		}
		posts = List.copyOf(posts);
		warnings = List.copyOf(warnings);
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
