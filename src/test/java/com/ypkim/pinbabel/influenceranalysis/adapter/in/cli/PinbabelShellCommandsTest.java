package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinbabelShellCommandsTest {

	@Test
	void mapsNaturalLanguageToApplicationUseCaseAndRendersOutcome() {
		AnalyzeInfluencerPostsUseCase useCase = command -> new AnalyzeInfluencerPostsResource(
			"0198d1bb-99e0-7000-8000-000000000001",
			"REFUSED",
			"domain only",
			null,
			"not investment advice",
			true,
			List.of()
		);

		var result = new PinbabelShellCommands(useCase, new PinbabelCliRenderer())
			.analyze("오늘 날씨를 알려줘");

		assertThat(result)
			.contains("status: REFUSED")
			.contains("runId: 0198d1bb-99e0-7000-8000-000000000001")
			.contains("traceAvailable: true")
			.contains("message: domain only")
			.contains("disclaimer: not investment advice");
	}
}
