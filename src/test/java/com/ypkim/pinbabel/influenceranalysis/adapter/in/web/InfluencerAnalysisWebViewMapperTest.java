package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InfluencerAnalysisWebViewMapperTest {

	private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
	private final InfluencerAnalysisWebViewMapper mapper = new InfluencerAnalysisWebViewMapper(
		new SsrAnalysisFailurePresenter(), Clock.fixed(NOW, ZoneOffset.UTC)
	);

	@Test
	void groupsEveryCompanyIntoOneOfFourOrderedSections() {
		var view = mapper.fixture("growth-lab", result(List.of(company("Microsoft", "POSITIVE", URI.create("urn:test")))));

		assertThat(view.sections()).extracting(section -> section.code())
			.containsExactly("POSITIVE", "NEGATIVE", "NEUTRAL", "UNCERTAIN");
		assertThat(view.sections().getFirst().companies()).singleElement().satisfies(company -> {
			assertThat(company.name()).isEqualTo("Microsoft");
			assertThat(company.mentionCount()).isEqualTo(1);
			assertThat(company.confidencePercent()).isEqualTo(90);
			assertThat(company.evidence().getFirst().externalLink()).isFalse();
		});
		assertThat(view.sections().get(1).companies()).isEmpty();
	}

	@Test
	void permitsOnlyExactHttpsXHostAsAnExternalEvidenceLink() {
		var view = mapper.fixture("growth-lab", result(List.of(
			company("Allowed", "POSITIVE", URI.create("https://x.com/a/status/1")),
			company("Deceptive", "NEGATIVE", URI.create("https://x.com.attacker.example/a"))
		)));

		assertThat(view.sections().getFirst().companies().getFirst().evidence().getFirst().externalLink()).isTrue();
		assertThat(view.sections().get(1).companies().getFirst().evidence().getFirst().externalLink()).isFalse();
	}

	@Test
	void stopsAutomaticPollingAfterThePersistedRunBudget() {
		var detail = new RecentXAnalysisDetailResource(
			"run", "correlation", "RUNNING", NOW.minusSeconds(120), NOW.minusSeconds(110), null,
			null, null, null, result(List.of())
		);

		var view = mapper.live(detail);

		assertThat(view.automaticPolling()).isFalse();
		assertThat(view.pollingBudgetExceeded()).isTrue();
		assertThat(view.terminal()).isFalse();
	}

	private RecentMentionedCompaniesResource result(List<RecentCompanyResource> companies) {
		return new RecentMentionedCompaniesResource(
			"COMPLETED", "done", "@account", 10, true, true, false,
			0, 0, 0, 0, 0, companies, List.of(), "not advice"
		);
	}

	private RecentCompanyResource company(String mention, String sentiment, URI uri) {
		return new RecentCompanyResource(
			mention, sentiment,
			"POSITIVE".equals(sentiment) ? 1 : 0,
			"NEGATIVE".equals(sentiment) ? 1 : 0,
			"NEUTRAL".equals(sentiment) ? 1 : 0,
			"UNCERTAIN".equals(sentiment) ? 1 : 0,
			false, 0.9,
			List.of(new RecentCompanyEvidenceResource(
				"post-1", NOW, uri, mention + " context", sentiment, "grounded rationale", 0.9
			))
		);
	}
}
