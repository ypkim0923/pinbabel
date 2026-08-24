package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record XAccountHandle(String username) {

	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,15}");

	public XAccountHandle {
		var normalized = username == null ? "" : username.strip();
		if (normalized.startsWith("@")) {
			normalized = normalized.substring(1);
		}
		if (!USERNAME.matcher(normalized).matches()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.X_USERNAME_INVALID,
				"X influencer identifier must be a valid username"
			);
		}
		username = normalized.toLowerCase(Locale.ROOT);
	}

	public String displayHandle() {
		return "@" + username;
	}
}
