package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.net.URI;
import java.time.Instant;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

@Entity
public record CollectedPost(
	@Identity String postId,
	String platform,
	String authorId,
	Instant publishedAt,
	URI url,
	String text,
	String source,
	PostKind kind
) {

	public CollectedPost {
		if (postId == null || postId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_ID_REQUIRED,
				"Post identifier is required"
			);
		}
		if (platform == null || platform.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_PLATFORM_REQUIRED,
				"Post platform is required"
			);
		}
		if (authorId == null || authorId.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_AUTHOR_REQUIRED,
				"Post author is required"
			);
		}
		if (publishedAt == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_PUBLISHED_AT_REQUIRED,
				"Post publication time is required"
			);
		}
		if (url == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_URL_REQUIRED,
				"Post URL is required"
			);
		}
		if (text == null || text.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_TEXT_REQUIRED,
				"Post text is required"
			);
		}
		if (source == null || source.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_SOURCE_REQUIRED,
				"Post source is required"
			);
		}
		if (kind == null) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.POST_KIND_REQUIRED,
				"Post kind is required"
			);
		}
	}
}
