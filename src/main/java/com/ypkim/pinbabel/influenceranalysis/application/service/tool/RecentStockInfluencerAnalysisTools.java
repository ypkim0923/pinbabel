package com.ypkim.pinbabel.influenceranalysis.application.service.tool;

import com.embabel.agent.api.annotation.LlmTool;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("fixture & x")
public class RecentStockInfluencerAnalysisTools {

	private final AnalyzeRecentXStockInfluencerUseCase useCase;

	public RecentStockInfluencerAnalysisTools(AnalyzeRecentXStockInfluencerUseCase useCase) {
		this.useCase = useCase;
	}

	@LlmTool(
		name = "list_recent_x_mentioned_companies",
		description = "List exact company expressions mentioned in an X account's ten most recent posts, excluding replies and simple reposts. No ticker normalization is performed."
	)
	public RecentMentionedCompaniesResource mentionedCompanies(
		@LlmTool.Param(
			description = "Exact X username or @handle, at most 15 username characters",
			required = true
		) String account
	) {
		return useCase.mentionedCompanies(account);
	}

	@LlmTool(
		name = "analyze_recent_x_company_sentiment",
		description = "List positively and negatively discussed company expressions from an X account's ten most recent posts, excluding replies and simple reposts. Reuses the bounded analysis cache when available."
	)
	public RecentCompanySentimentResource companySentiment(
		@LlmTool.Param(
			description = "Exact X username or @handle, at most 15 username characters",
			required = true
		) String account
	) {
		return useCase.companySentiment(account);
	}
}
