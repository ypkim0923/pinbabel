package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.stereotype.Component;

@Component
@PrimaryAdapter
public class A2UiSnapshotRenderer {
	static final String VERSION = "v0.9";
	static final String CATALOG = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json";

	private final ObjectMapper objectMapper;

	public A2UiSnapshotRenderer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String render(AnalysisRunDetailResource run) {
		var surfaceId = "pinbabel-analysis-" + run.runId();
		return String.join("\n",
			json(Map.of("version", VERSION, "createSurface", Map.of(
				"surfaceId", surfaceId, "catalogId", CATALOG
			))),
			json(Map.of("version", VERSION, "updateComponents", Map.of(
				"surfaceId", surfaceId,
				"components", List.of(
					Map.of("id", "root", "component", "Column", "children", List.of("title", "status", "summary", "disclaimer")),
					Map.of("id", "title", "component", "Text", "text", "Pinbabel 주식 인플루언서 분석", "variant", "h1"),
					Map.of("id", "status", "component", "Text", "text", Map.of("path", "/status")),
					Map.of("id", "summary", "component", "Text", "text", Map.of("path", "/outcomeSummary")),
					Map.of("id", "disclaimer", "component", "Text", "text", Map.of("path", "/disclaimer"))
				)
			))),
			json(Map.of("version", VERSION, "updateDataModel", Map.of(
				"surfaceId", surfaceId, "path", "/", "value", dataModel(run)
			)))
		) + "\n";
	}

	private Map<String, Object> dataModel(AnalysisRunDetailResource run) {
		var value = new LinkedHashMap<String, Object>();
		value.put("runId", run.runId());
		value.put("correlationId", run.correlationId());
		value.put("status", run.status());
		value.put("outcomeCode", run.outcomeCode());
		value.put("outcomeSummary", run.outcomeSummary() == null ? "분석이 진행 중입니다." : run.outcomeSummary());
		value.put("traceAvailable", run.traceAvailable());
		value.put("metrics", Map.of(
			"models", run.metrics().models(),
			"promptTokens", run.metrics().promptTokens() == null ? 0 : run.metrics().promptTokens(),
			"completionTokens", run.metrics().completionTokens() == null ? 0 : run.metrics().completionTokens(),
			"costUsd", run.metrics().costUsd() == null ? 0 : run.metrics().costUsd()
		));
		value.put("events", run.events().stream().map(event -> {
			var item = new LinkedHashMap<String, Object>();
			item.put("sequence", event.sequence());
			item.put("occurredAt", event.occurredAt().toString());
			item.put("eventType", event.eventType());
			if (event.actionName() != null) item.put("actionName", event.actionName());
			if (event.toolName() != null) item.put("toolName", event.toolName());
			if (event.modelName() != null) item.put("modelName", event.modelName());
			if (event.durationMs() != null) item.put("durationMs", event.durationMs());
			if (event.successful() != null) item.put("successful", event.successful());
			return item;
		}).toList());
		value.put("platform", run.report() == null ? null : run.report().platform());
		value.put("influencerId", run.report() == null ? null : run.report().influencerId());
		value.put("period", run.report() == null ? null : Map.of(
			"startInclusive", run.report().period().startInclusive().toString(),
			"endExclusive", run.report().period().endExclusive().toString(),
			"timezone", run.report().period().timezone()
		));
		value.put("instruments", run.report() == null ? List.of() : run.report().instrumentSummaries().stream()
			.map(item -> new A2UiInstrumentDto(
				item.instrumentId(), item.ticker(), item.displayName(), item.overallSentiment(),
				item.positiveCount(), item.negativeCount(), item.neutralCount(), item.uncertainCount(),
				item.conflicting(), item.evidencePostIds()
			))
			.toList());
		value.put("evidence", run.report() == null ? List.of() : run.report().evidence().stream()
			.map(item -> new A2UiEvidenceDto(
				item.postId(), item.platform(), item.authorId(), item.publishedAt().toString(), item.url(),
				item.source(), item.instrumentId(), item.ticker(), item.sentiment(), item.excerpt(), item.rationale()
			))
			.toList());
		value.put("warnings", run.report() == null ? List.of() : run.report().warnings());
		value.put("disclaimer", run.report() == null
			? "공개 SNS 발언에 대한 자동 분석이며 투자 자문이 아닙니다."
			: run.report().disclaimer());
		return value;
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("A2UI snapshot serialization failed", exception);
		}
	}

	private record A2UiInstrumentDto(
		String instrumentId,
		String ticker,
		String displayName,
		String sentiment,
		int positiveCount,
		int negativeCount,
		int neutralCount,
		int uncertainCount,
		boolean conflicting,
		List<String> evidencePostIds
	) {
	}

	private record A2UiEvidenceDto(
		String postId,
		String platform,
		String authorId,
		String publishedAt,
		String url,
		String source,
		String instrumentId,
		String ticker,
		String sentiment,
		String excerpt,
		String rationale
	) {
	}
}
