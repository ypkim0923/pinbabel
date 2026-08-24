package com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record InfluencerProfileId(String value) {

	private static final Pattern ALLOWED = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	public InfluencerProfileId {
		if (value == null || !ALLOWED.matcher(value).matches() || value.length() > 40) {
			throw new IllegalArgumentException("Influencer profile identifier is invalid");
		}
	}
}
