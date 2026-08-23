package com.ypkim.pinbabel.influenceranalysis.application.port.in;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface AnalyzeInfluencerPostsUseCase {

	AnalyzeInfluencerPostsResource analyze(AnalyzeInfluencerPostsCommand command);
}
