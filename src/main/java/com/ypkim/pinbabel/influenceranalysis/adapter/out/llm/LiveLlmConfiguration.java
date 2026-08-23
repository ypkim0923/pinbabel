package com.ypkim.pinbabel.influenceranalysis.adapter.out.llm;

import com.embabel.agent.openai.OpenAiCompatibleModelFactory;
import com.embabel.agent.spi.LlmService;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import java.net.URI;
import java.net.URISyntaxException;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("live-openai")
@SecondaryAdapter
public class LiveLlmConfiguration {

	@Bean
	LlmService<?> pinbabelOpenAiLlm(
		Environment environment,
		@Value("${pinbabel.llm.model}") String model,
		@Value("${pinbabel.llm.provider}") String provider
	) {
		var settings = settingsFrom(environment, model, provider);
		try {
			return OpenAiCompatibleModelFactory.Companion.byok(
				settings.baseUrl().toString(),
				settings.apiKey(),
				settings.model(),
				settings.provider()
			).buildValidated();
		} catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.LLM_VALIDATION_FAILED,
				"OpenAI-compatible LLM validation failed"
			);
		}
	}

	static LiveLlmSettings settingsFrom(Environment environment, String model, String provider) {
		var apiKey = environment.getProperty("OPENAI_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.LLM_API_KEY_REQUIRED,
				"OPENAI_API_KEY is required for the live-openai profile"
			);
		}
		var baseUrl = environment.getProperty("OPENAI_BASE_URL");
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.LLM_BASE_URL_REQUIRED,
				"OPENAI_BASE_URL is required for the live-openai profile"
			);
		}
		return new LiveLlmSettings(apiKey, validateBaseUrl(baseUrl), model, provider);
	}

	static URI validateBaseUrl(String value) {
		try {
			var uri = new URI(value);
			var valid = "https".equalsIgnoreCase(uri.getScheme())
				&& uri.getHost() != null
				&& uri.getUserInfo() == null
				&& uri.getFragment() == null;
			if (!valid) {
				throw invalidBaseUrl();
			}
			return uri;
		} catch (URISyntaxException exception) {
			throw invalidBaseUrl();
		}
	}

	private static InfluencerAnalysisException invalidBaseUrl() {
		return new InfluencerAnalysisException(
			InfluencerAnalysisInternalCode.LLM_BASE_URL_INVALID,
			"OPENAI_BASE_URL must be an absolute HTTPS URL without user-info or fragment"
		);
	}

	record LiveLlmSettings(String apiKey, URI baseUrl, String model, String provider) {
	}
}
