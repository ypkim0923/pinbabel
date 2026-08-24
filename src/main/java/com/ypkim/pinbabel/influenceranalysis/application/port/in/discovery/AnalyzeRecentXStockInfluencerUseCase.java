package com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentCompanySentimentResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface AnalyzeRecentXStockInfluencerUseCase {

	RecentMentionedCompaniesResource mentionedCompanies(String account);

	RecentCompanySentimentResource companySentiment(String account);
}
