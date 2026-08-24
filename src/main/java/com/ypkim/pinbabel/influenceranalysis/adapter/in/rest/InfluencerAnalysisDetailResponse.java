package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.InfluencerAnalysisReportResource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record InfluencerAnalysisDetailResponse(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	Long durationMs,
	boolean traceAvailable,
	String warningCode,
	String outcomeCode,
	String outcomeSummary,
	MetricsDto metrics,
	ReportDto report,
	List<EventDto> events
) {
	static InfluencerAnalysisDetailResponse from(AnalysisRunDetailResource resource) {
		var metrics = resource.metrics();
		return new InfluencerAnalysisDetailResponse(
			resource.runId(), resource.correlationId(), resource.status(), resource.createdAt(),
			resource.startedAt(), resource.completedAt(), resource.durationMs(), resource.traceAvailable(),
			resource.warningCode(), resource.outcomeCode(), resource.outcomeSummary(),
			new MetricsDto(metrics.promptTokens(), metrics.completionTokens(), metrics.costUsd(), metrics.models()),
			ReportDto.from(resource.report()),
			resource.events().stream().map(event -> new EventDto(
				event.sequence(), event.occurredAt(), event.eventType(), event.actionName(), event.toolName(),
				event.modelName(), event.durationMs(), event.successful()
			)).toList()
		);
	}

	public record MetricsDto(Long promptTokens, Long completionTokens, BigDecimal costUsd, List<String> models) {
		public MetricsDto { models = List.copyOf(models); }
	}

	public record ReportDto(
		String platform,
		String influencerId,
		PeriodDto period,
		List<InstrumentDto> instruments,
		List<EvidenceDto> evidence,
		List<String> warnings,
		String disclaimer
	) {
		static ReportDto from(InfluencerAnalysisReportResource resource) {
			if (resource == null) return null;
			return new ReportDto(
				resource.platform(), resource.influencerId(),
				new PeriodDto(resource.period().startInclusive(), resource.period().endExclusive(), resource.period().timezone()),
				resource.instrumentSummaries().stream().map(item -> new InstrumentDto(
					item.instrumentId(), item.ticker(), item.displayName(), item.overallSentiment(),
					item.positiveCount(), item.negativeCount(), item.neutralCount(), item.uncertainCount(),
					item.conflicting(), item.evidencePostIds()
				)).toList(),
				resource.evidence().stream().map(item -> new EvidenceDto(
					item.postId(), item.platform(), item.authorId(), item.publishedAt(), item.url(), item.source(),
					item.instrumentId(), item.ticker(), item.sentiment(), item.excerpt(), item.rationale()
				)).toList(),
				resource.warnings(), resource.disclaimer()
			);
		}
	}

	public record PeriodDto(Instant startInclusive, Instant endExclusive, String timezone) {}

	public record InstrumentDto(
		String instrumentId, String ticker, String displayName, String sentiment,
		int positiveCount, int negativeCount, int neutralCount, int uncertainCount,
		boolean conflicting, List<String> evidencePostIds
	) { public InstrumentDto { evidencePostIds = List.copyOf(evidencePostIds); } }

	public record EvidenceDto(
		String postId, String platform, String authorId, Instant publishedAt, String url, String source,
		String instrumentId, String ticker, String sentiment, String excerpt, String rationale
	) {}

	public record EventDto(
		long sequence, Instant occurredAt, String eventType, String actionName, String toolName,
		String modelName, Long durationMs, Boolean successful
	) {}
}
