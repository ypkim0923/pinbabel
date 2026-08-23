package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Profile("fixture & cli")
@PrimaryAdapter
public class PinbabelShellCommands {

	private final AnalyzeInfluencerPostsUseCase useCase;
	private final PinbabelCliRenderer renderer;

	public PinbabelShellCommands(AnalyzeInfluencerPostsUseCase useCase, PinbabelCliRenderer renderer) {
		this.useCase = useCase;
		this.renderer = renderer;
	}

	@ShellMethod(
		key = "pinbabel",
		value = "Analyze stock influencer SNS posts for an explicit period"
	)
	public String analyze(
		@ShellOption(help = "Natural-language stock influencer post analysis request") String intent
	) {
		return renderer.render(useCase.analyze(new AnalyzeInfluencerPostsCommand(intent)));
	}
}
