package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisOutcome;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.RecentCompanyAnalysisService;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryFixtureStockInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.FixtureRecentAnalysisSource;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture")
public class FixtureStockInfluencerAnalysisService implements QueryFixtureStockInfluencerAnalysisUseCase {

	private final FixtureRecentAnalysisSource source;
	private final RecentCompanyAnalysisService analysisService = new RecentCompanyAnalysisService();

	public FixtureStockInfluencerAnalysisService(FixtureRecentAnalysisSource source) {
		this.source = source;
	}

	@Override
	public Optional<RecentMentionedCompaniesResource> findAnalysis(InfluencerProfileId profileId) {
		return source.findByProfileId(profileId).map(scenario -> {
			var result = analysisService.summarize(
				scenario.posts(), scenario.assessments(), scenario.analyzedTextByPostId()
			);
			var companies = result.companies().stream()
				.sorted(Comparator.comparing(summary -> summary.mention()))
				.map(summary -> new RecentCompanyResource(
					summary.mention(), summary.overallSentiment().name(), summary.positiveCount(),
					summary.negativeCount(), summary.neutralCount(), summary.uncertainCount(),
					summary.conflicting(), summary.confidence(),
					summary.evidence().stream().map(evidence -> new RecentCompanyEvidenceResource(
						evidence.postId(), evidence.publishedAt(), evidence.sourceUrl(), evidence.excerpt(),
						evidence.sentiment().name(), evidence.rationale(), evidence.confidence()
					)).toList()
				))
				.toList();
			var warnings = java.util.stream.Stream.concat(
				scenario.posts().warnings().stream(), result.warnings().stream()
			).distinct().toList();
			return new RecentMentionedCompaniesResource(
				"COMPLETED",
				"고정 Fixture 포스트 10개를 외부 호출 없이 분석했습니다.",
				scenario.account().displayHandle(),
				scenario.posts().posts().size(),
				true,
				true,
				false,
				0,
				0,
				0,
				0,
				0,
				companies,
				warnings,
				InfluencerAnalysisOutcome.DISCLAIMER
			);
		});
	}
}
