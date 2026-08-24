package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinbabelRecentDiscoveryShellCommandsTest {

	@Test
	void rendersMentionAndSentimentCommandsWithCostMetadata() {
		var progress = new java.util.ArrayList<String>();
		var commands = new PinbabelRecentDiscoveryShellCommands(
			useCase(), new PinbabelCliRenderer(), progress::add
		);

		var mentions = commands.mentionedCompanies("@aleabitoreddit");
		var sentiment = commands.companySentiment("@aleabitoreddit");

		assertThat(mentions)
			.contains("account: @aleabitoreddit", "companies: 1", "Microsoft")
			.contains("confidence: 0.9000", "source rationale", "Microsoft source excerpt")
			.contains("https://x.com/example/status/post-1", "2026-08-24T00:00:00Z")
			.contains("commentsExcluded: true", "repostsExcluded: true")
			.contains("xApiRequestsThisCall: 2", "llmCallsThisCall: 1")
			.contains("xApiRequestBudget: 2", "llmCallBudget: 1", "durationMs: 123");
		assertThat(sentiment)
			.contains("positiveCompanies: 0", "negativeCompanies: 1")
			.contains("cacheHit: true", "xApiRequestsThisCall: 0");
		assertThat(progress)
			.hasSize(4)
			.anySatisfy(message -> assertThat(message).contains("timeout 60초"))
			.endsWith("분석이 완료되었습니다.");
	}

	private AnalyzeRecentXStockInfluencerUseCase useCase() {
		var company = new RecentCompanyResource(
			"Microsoft", "NEGATIVE",
			0, 1, 0, 0, false, 0.9,
			List.of(new RecentCompanyEvidenceResource(
				"post-1",
				Instant.parse("2026-08-24T00:00:00Z"),
				URI.create("https://x.com/example/status/post-1"),
				"Microsoft source excerpt",
				"NEGATIVE",
				"source rationale",
				0.9
			))
		);
		return new AnalyzeRecentXStockInfluencerUseCase() {
			@Override
			public RecentMentionedCompaniesResource mentionedCompanies(String account) {
				return new RecentMentionedCompaniesResource(
					"COMPLETED", "done", account, 10, true, true, false, 2, 1, 2, 1, 123,
					List.of(company), List.of(), "not investment advice"
				);
			}

			@Override
			public RecentCompanySentimentResource companySentiment(String account) {
				return new RecentCompanySentimentResource(
					"COMPLETED", "cached", account, 10, true, true, true, 0, 0, 2, 1, 0,
					List.of(), List.of(company), List.of(), "not investment advice"
				);
			}
		};
	}
}
