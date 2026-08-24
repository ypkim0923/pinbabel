package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record RecentXAnalysisDetailResponse(
	String runId,
	String correlationId,
	String status,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	Long durationMs,
	String outcomeCode,
	String outcomeSummary,
	ResultDto result
) {
	static RecentXAnalysisDetailResponse from(RecentXAnalysisDetailResource resource) {
		var result = resource.result();
		return new RecentXAnalysisDetailResponse(
			resource.runId(), resource.correlationId(), resource.status(), resource.createdAt(), resource.startedAt(),
			resource.completedAt(), resource.durationMs(), resource.outcomeCode(), resource.outcomeSummary(),
			new ResultDto(
				result.status(), result.message(), result.account(), result.analyzedPostCount(), result.commentsExcluded(),
				result.repostsExcluded(), result.cacheHit(), result.xApiRequestsThisCall(), result.llmCallsThisCall(),
				result.xApiRequestBudget(), result.llmCallBudget(), result.durationMs(),
				result.companies().stream().map(CompanyDto::from).toList(), result.warnings(), result.disclaimer()
			)
		);
	}

	public record ResultDto(
		String status, String message, String account, int analyzedPostCount,
		boolean commentsExcluded, boolean repostsExcluded, boolean cacheHit,
		Integer xApiRequestsThisCall, Integer llmCallsThisCall,
		int xApiRequestBudget, int llmCallBudget, long durationMs,
		List<CompanyDto> companies, List<String> warnings, String disclaimer
	) {
		public ResultDto { companies = List.copyOf(companies); warnings = List.copyOf(warnings); }
	}

	public record CompanyDto(
		String mention, String overallSentiment, int positiveCount, int negativeCount,
		int neutralCount, int uncertainCount, boolean conflicting, double confidence,
		List<EvidenceDto> evidence
	) {
		public CompanyDto { evidence = List.copyOf(evidence); }

		static CompanyDto from(RecentCompanyResource resource) {
			return new CompanyDto(
				resource.mention(), resource.overallSentiment(), resource.positiveCount(), resource.negativeCount(),
				resource.neutralCount(), resource.uncertainCount(), resource.conflicting(), resource.confidence(),
				resource.evidence().stream().map(EvidenceDto::from).toList()
			);
		}
	}

	public record EvidenceDto(
		String postId, Instant publishedAt, URI sourceUrl, String excerpt,
		String sentiment, String rationale, double confidence
	) {
		static EvidenceDto from(RecentCompanyEvidenceResource resource) {
			return new EvidenceDto(
				resource.postId(), resource.publishedAt(), resource.sourceUrl(), resource.excerpt(),
				resource.sentiment(), resource.rationale(), resource.confidence()
			);
		}
	}
}
