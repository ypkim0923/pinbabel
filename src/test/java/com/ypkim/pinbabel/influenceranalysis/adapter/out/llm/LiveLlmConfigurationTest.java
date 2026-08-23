package com.ypkim.pinbabel.influenceranalysis.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LiveLlmConfigurationTest {

	@Test
	void readsKeyAndHttpsBaseUrlFromEnvironment() {
		var environment = new MockEnvironment()
			.withProperty("OPENAI_API_KEY", "test-secret")
			.withProperty("OPENAI_BASE_URL", "https://api.example.test/v1");

		var settings = LiveLlmConfiguration.settingsFrom(environment, "gpt-4.1-mini", "OpenAI-compatible");

		assertThat(settings.apiKey()).isEqualTo("test-secret");
		assertThat(settings.baseUrl()).hasScheme("https").hasHost("api.example.test");
		assertThat(settings.model()).isEqualTo("gpt-4.1-mini");
	}

	@Test
	void rejectsMissingKeyWithoutContactingProvider() {
		var environment = new MockEnvironment().withProperty("OPENAI_BASE_URL", "https://api.example.test/v1");

		assertThatThrownBy(() -> LiveLlmConfiguration.settingsFrom(environment, "model", "provider"))
			.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception ->
				assertThat(exception.internalCode()).isEqualTo(InfluencerAnalysisInternalCode.LLM_API_KEY_REQUIRED)
			);
	}

	@Test
	void rejectsUnsafeBaseUrlsWithoutEchoingTheirValues() {
		for (var baseUrl : Map.of(
			"plain-http", "http://api.example.test/v1",
			"userinfo", "https://user:secret@api.example.test/v1",
			"fragment", "https://api.example.test/v1#secret"
		).values()) {
			assertThatThrownBy(() -> LiveLlmConfiguration.validateBaseUrl(baseUrl))
				.isInstanceOfSatisfying(InfluencerAnalysisException.class, exception -> {
					assertThat(exception.internalCode()).isEqualTo(InfluencerAnalysisInternalCode.LLM_BASE_URL_INVALID);
					assertThat(exception.getMessage()).doesNotContain(baseUrl);
				});
		}
	}
}
