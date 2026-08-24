package com.ypkim.pinbabel.influenceranalysis.application.service.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisCorrelationId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyEvidenceResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanyResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisExecutionLauncher;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.RecentXAnalysisResultStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredRecentXAnalysisResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Profile("fixture & x")
public class RecentXAnalysisExecutionService implements SubmitRecentXAnalysisUseCase, QueryRecentXAnalysisUseCase {
	private static final Logger log = LoggerFactory.getLogger(RecentXAnalysisExecutionService.class);

	private final AnalyzeRecentXStockInfluencerUseCase analysisUseCase;
	private final QueryAnalysisRunsUseCase runsQueryUseCase;
	private final AnalysisRunStore runStore;
	private final RecentXAnalysisResultStore resultStore;
	private final AnalysisExecutionLauncher executionLauncher;
	private final Clock clock;

	@Autowired
	public RecentXAnalysisExecutionService(
		AnalyzeRecentXStockInfluencerUseCase analysisUseCase,
		QueryAnalysisRunsUseCase runsQueryUseCase,
		AnalysisRunStore runStore,
		RecentXAnalysisResultStore resultStore,
		AnalysisExecutionLauncher executionLauncher
	) {
		this(analysisUseCase, runsQueryUseCase, runStore, resultStore, executionLauncher, Clock.systemUTC());
	}

	RecentXAnalysisExecutionService(
		AnalyzeRecentXStockInfluencerUseCase analysisUseCase,
		QueryAnalysisRunsUseCase runsQueryUseCase,
		AnalysisRunStore runStore,
		RecentXAnalysisResultStore resultStore,
		AnalysisExecutionLauncher executionLauncher,
		Clock clock
	) {
		this.analysisUseCase = analysisUseCase;
		this.runsQueryUseCase = runsQueryUseCase;
		this.runStore = runStore;
		this.resultStore = resultStore;
		this.executionLauncher = executionLauncher;
		this.clock = clock;
	}

	@Override
	public AnalysisSubmissionResource submit(String rawAccount) {
		var run = AnalysisRun.create(AnalysisRunId.newId(), AnalysisCorrelationId.newId(), now());
		runStore.save(run, null);
		final XAccountHandle account;
		try {
			account = new XAccountHandle(rawAccount);
		}
		catch (RuntimeException exception) {
			run.reject(now(), "INVALID_X_ACCOUNT", "유효한 X 계정을 입력해 주세요.");
			runStore.save(run, null);
			resultStore.save(run.id(), emptyResult("FAILED", run.outcomeSummary(), ""));
			return AnalysisSubmissionResource.from(run);
		}

		resultStore.save(run.id(), emptyResult("CREATED", "분석 실행을 기다리고 있습니다.", account.displayHandle()));
		if (!executionLauncher.launch(run.id(), () -> execute(run, account))) {
			run.reject(now(), "EXECUTION_CAPACITY_EXCEEDED", "Analysis execution capacity is exhausted");
			runStore.save(run, null);
			resultStore.save(run.id(), emptyResult("FAILED", run.outcomeSummary(), account.displayHandle()));
		}
		return AnalysisSubmissionResource.from(run);
	}

	@Override
	public Optional<RecentXAnalysisDetailResource> findRecentRun(AnalysisRunId runId) {
		var result = resultStore.findByRunId(runId);
		if (result.isEmpty()) return Optional.empty();
		return runsQueryUseCase.findRun(runId).map(run -> toDetail(run, result.orElseThrow()));
	}

	private void execute(AnalysisRun run, XAccountHandle account) {
		run.start(now());
		runStore.save(run, null);
		try {
			var result = analysisUseCase.mentionedCompanies(account.displayHandle());
			resultStore.save(run.id(), toStored(result));
			if ("COMPLETED".equals(result.status())) {
				run.complete(now(), "RECENT_X_ANALYSIS_COMPLETED", result.message());
			}
			else {
				var code = result.warnings().isEmpty() ? "RECENT_X_ANALYSIS_FAILED" : result.warnings().getFirst();
				run.fail(now(), code, result.message());
			}
		}
		catch (RuntimeException exception) {
			log.warn("Recent X analysis execution failed: runId={}", run.id().value());
			run.fail(now(), "RECENT_X_ANALYSIS_EXECUTION_FAILED", "최근 X 분석을 완료하지 못했습니다.");
			try {
				resultStore.save(run.id(), emptyResult("FAILED", run.outcomeSummary(), account.displayHandle()));
			}
			catch (RuntimeException ignored) {
				log.warn("Recent X result recording failed: runId={}", run.id().value());
			}
		}
		runStore.save(run, null);
	}

	private RecentXAnalysisDetailResource toDetail(AnalysisRunDetailResource run, StoredRecentXAnalysisResult stored) {
		return new RecentXAnalysisDetailResource(
			run.runId(), run.correlationId(), run.status(), run.createdAt(), run.startedAt(), run.completedAt(),
			run.durationMs(), run.outcomeCode(), run.outcomeSummary(), toResource(stored)
		);
	}

	private StoredRecentXAnalysisResult toStored(RecentMentionedCompaniesResource result) {
		return new StoredRecentXAnalysisResult(
			result.status(), result.message(), result.account(), result.analyzedPostCount(), result.commentsExcluded(),
			result.repostsExcluded(), result.cacheHit(), result.xApiRequestsThisCall(), result.llmCallsThisCall(),
			result.xApiRequestBudget(), result.llmCallBudget(), result.durationMs(),
			result.companies().stream().map(company -> new StoredRecentXAnalysisResult.Company(
				company.mention(), company.overallSentiment(), company.positiveCount(), company.negativeCount(),
				company.neutralCount(), company.uncertainCount(), company.conflicting(), company.confidence(),
				company.evidence().stream().map(evidence -> new StoredRecentXAnalysisResult.Evidence(
					evidence.postId(), evidence.publishedAt(), evidence.sourceUrl(), evidence.excerpt(),
					evidence.sentiment(), evidence.rationale(), evidence.confidence()
				)).toList()
			)).toList(), result.warnings(), result.disclaimer()
		);
	}

	private RecentMentionedCompaniesResource toResource(StoredRecentXAnalysisResult result) {
		return new RecentMentionedCompaniesResource(
			result.status(), result.message(), result.account(), result.analyzedPostCount(), result.commentsExcluded(),
			result.repostsExcluded(), result.cacheHit(), result.xApiRequestsThisCall(), result.llmCallsThisCall(),
			result.xApiRequestBudget(), result.llmCallBudget(), result.durationMs(),
			result.companies().stream().map(company -> new RecentCompanyResource(
				company.mention(), company.overallSentiment(), company.positiveCount(), company.negativeCount(),
				company.neutralCount(), company.uncertainCount(), company.conflicting(), company.confidence(),
				company.evidence().stream().map(evidence -> new RecentCompanyEvidenceResource(
					evidence.postId(), evidence.publishedAt(), evidence.sourceUrl(), evidence.excerpt(),
					evidence.sentiment(), evidence.rationale(), evidence.confidence()
				)).toList()
			)).toList(), result.warnings(), result.disclaimer()
		);
	}

	private StoredRecentXAnalysisResult emptyResult(String status, String message, String account) {
		return new StoredRecentXAnalysisResult(
			status, message, account == null ? "" : account, 0, true, true, false,
			0, 0, 2, 1, 0, List.of(), List.of(),
			"공개 SNS 발언에 대한 자동 분석이며 투자 자문이 아닙니다."
		);
	}

	private Instant now() {
		return clock.instant();
	}
}
