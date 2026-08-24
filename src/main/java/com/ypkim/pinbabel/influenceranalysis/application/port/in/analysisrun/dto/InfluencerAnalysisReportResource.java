package com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import java.time.Instant;
import java.util.List;

public record InfluencerAnalysisReportResource(
	String platform,
	String influencerId,
	PeriodResource period,
	List<InstrumentSummaryResource> instrumentSummaries,
	List<EvidenceResource> evidence,
	List<String> warnings,
	String disclaimer
) {
	public InfluencerAnalysisReportResource {
		instrumentSummaries = List.copyOf(instrumentSummaries);
		evidence = List.copyOf(evidence);
		warnings = List.copyOf(warnings);
	}

	public static InfluencerAnalysisReportResource from(InfluencerAnalysisReport report) {
		if (report == null) {
			return null;
		}
		return new InfluencerAnalysisReportResource(
			report.platform(),
			report.influencerId(),
			new PeriodResource(
				report.period().startInclusive(),
				report.period().endExclusive(),
				report.period().timezone().getId()
			),
			report.instrumentSummaries().stream()
				.map(summary -> new InstrumentSummaryResource(
					summary.instrumentId(), summary.ticker(), summary.displayName(),
					summary.overallSentiment().name(), summary.positiveCount(), summary.negativeCount(),
					summary.neutralCount(), summary.uncertainCount(), summary.conflicting(),
					summary.evidencePostIds()
				))
				.toList(),
			report.evidence().stream()
				.map(item -> new EvidenceResource(
					item.postId(), item.platform(), item.authorId(), item.publishedAt(), item.url(),
					item.source(), item.instrumentId(), item.ticker(), item.sentiment().name(),
					item.excerpt(), item.rationale()
				))
				.toList(),
			report.warnings(),
			report.disclaimer()
		);
	}

	public record PeriodResource(Instant startInclusive, Instant endExclusive, String timezone) {
	}

	public record InstrumentSummaryResource(
		String instrumentId,
		String ticker,
		String displayName,
		String overallSentiment,
		int positiveCount,
		int negativeCount,
		int neutralCount,
		int uncertainCount,
		boolean conflicting,
		List<String> evidencePostIds
	) {
		public InstrumentSummaryResource {
			evidencePostIds = List.copyOf(evidencePostIds);
		}
	}

	public record EvidenceResource(
		String postId,
		String platform,
		String authorId,
		Instant publishedAt,
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
