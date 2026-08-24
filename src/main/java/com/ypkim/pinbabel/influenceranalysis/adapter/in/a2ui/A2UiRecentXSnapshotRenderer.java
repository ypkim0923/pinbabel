package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@PrimaryAdapter
public class A2UiRecentXSnapshotRenderer {

	private final ObjectMapper objectMapper;

	public A2UiRecentXSnapshotRenderer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String render(RecentXAnalysisDetailResource run) {
		var surfaceId = "pinbabel-recent-x-" + run.runId();
		return String.join("\n",
			json(Map.of("version", A2UiSnapshotRenderer.VERSION, "createSurface", Map.of(
				"surfaceId", surfaceId, "catalogId", A2UiSnapshotRenderer.CATALOG
			))),
			json(Map.of("version", A2UiSnapshotRenderer.VERSION, "updateComponents", Map.of(
				"surfaceId", surfaceId,
				"components", List.of(
					Map.of("id", "root", "component", "Column", "children", List.of("title", "account", "status", "summary", "disclaimer")),
					Map.of("id", "title", "component", "Text", "text", "최근 X 회사·감정 분석", "variant", "h1"),
					Map.of("id", "account", "component", "Text", "text", Map.of("path", "/account")),
					Map.of("id", "status", "component", "Text", "text", Map.of("path", "/status")),
					Map.of("id", "summary", "component", "Text", "text", Map.of("path", "/outcomeSummary")),
					Map.of("id", "disclaimer", "component", "Text", "text", Map.of("path", "/disclaimer"))
				)
			))),
			json(Map.of("version", A2UiSnapshotRenderer.VERSION, "updateDataModel", Map.of(
				"surfaceId", surfaceId, "path", "/", "value", dataModel(run)
			)))
		) + "\n";
	}

	private Map<String, Object> dataModel(RecentXAnalysisDetailResource run) {
		var result = run.result();
		return Map.ofEntries(
			Map.entry("runId", run.runId()),
			Map.entry("correlationId", run.correlationId()),
			Map.entry("status", run.status()),
			Map.entry("outcomeSummary", run.outcomeSummary() == null ? "분석이 진행 중입니다." : run.outcomeSummary()),
			Map.entry("account", result.account()),
			Map.entry("analyzedPostCount", result.analyzedPostCount()),
			Map.entry("commentsExcluded", result.commentsExcluded()),
			Map.entry("repostsExcluded", result.repostsExcluded()),
			Map.entry("companies", result.companies().stream().map(company -> Map.<String, Object>of(
				"mention", company.mention(),
				"overallSentiment", company.overallSentiment(),
				"positiveCount", company.positiveCount(),
				"negativeCount", company.negativeCount(),
				"neutralCount", company.neutralCount(),
				"uncertainCount", company.uncertainCount(),
				"conflicting", company.conflicting(),
				"confidence", company.confidence(),
				"evidence", company.evidence().stream().map(evidence -> Map.<String, Object>of(
					"postId", evidence.postId(),
					"publishedAt", evidence.publishedAt().toString(),
					"sourceUrl", evidence.sourceUrl().toString(),
					"excerpt", evidence.excerpt(),
					"sentiment", evidence.sentiment(),
					"rationale", evidence.rationale(),
					"confidence", evidence.confidence()
				)).toList()
			)).toList()),
			Map.entry("warnings", result.warnings()),
			Map.entry("budgets", Map.of(
				"xApiRequestBudget", result.xApiRequestBudget(), "llmCallBudget", result.llmCallBudget()
			)),
			Map.entry("disclaimer", result.disclaimer())
		);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException exception) {
			throw new IllegalStateException("A2UI recent X snapshot serialization failed", exception);
		}
	}
}
