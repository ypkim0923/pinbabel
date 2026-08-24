package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import org.junit.jupiter.api.Test;

class XAccountHandleTest {

	@Test
	void normalizesAtPrefixWhitespaceAndCase() {
		var handle = new XAccountHandle("  @AleaBitoReddit  ");

		assertThat(handle.username()).isEqualTo("aleabitoreddit");
		assertThat(handle.displayHandle()).isEqualTo("@aleabitoreddit");
	}

	@Test
	void rejectsMissingOrMalformedUsernames() {
		assertThatThrownBy(() -> new XAccountHandle(null)).isInstanceOfSatisfying(
			InfluencerAnalysisException.class,
			exception -> assertThat(exception.internalCode())
				.isEqualTo(InfluencerAnalysisInternalCode.X_USERNAME_INVALID)
		);
		assertThatThrownBy(() -> new XAccountHandle("name-with-hyphen")).isInstanceOf(
			InfluencerAnalysisException.class
		);
		assertThatThrownBy(() -> new XAccountHandle("1234567890123456")).isInstanceOf(
			InfluencerAnalysisException.class
		);
	}
}
