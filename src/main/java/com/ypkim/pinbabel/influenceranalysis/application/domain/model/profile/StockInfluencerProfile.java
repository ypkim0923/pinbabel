package com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import java.util.Set;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record StockInfluencerProfile(
	@Identity InfluencerProfileId id,
	XAccountHandle handle,
	String displayName,
	String description,
	String investmentStyle,
	InfluencerProfileSource source,
	String avatarInitials,
	String avatarColor
) {
	private static final Set<String> ALLOWED_COLORS = Set.of(
		"teal", "amber", "coral", "blue", "olive", "rose", "slate", "gold", "aqua", "clay"
	);

	public StockInfluencerProfile {
		if (id == null || handle == null || source == null) {
			throw new IllegalArgumentException("Influencer profile identity and source are required");
		}
		displayName = requireText(displayName, "Influencer display name is required");
		description = requireText(description, "Influencer description is required");
		investmentStyle = requireText(investmentStyle, "Influencer investment style is required");
		avatarInitials = requireText(avatarInitials, "Influencer avatar initials are required");
		if (avatarInitials.length() > 3) {
			throw new IllegalArgumentException("Influencer avatar initials are too long");
		}
		if (!ALLOWED_COLORS.contains(avatarColor)) {
			throw new IllegalArgumentException("Influencer avatar color is invalid");
		}
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.strip();
	}
}
