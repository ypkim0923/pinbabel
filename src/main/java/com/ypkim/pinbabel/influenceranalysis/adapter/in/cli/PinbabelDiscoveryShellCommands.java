package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.RecommendXStockInfluencersUseCase;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
@Profile("fixture & cli")
@PrimaryAdapter
public class PinbabelDiscoveryShellCommands {

	private final RecommendXStockInfluencersUseCase recommendUseCase;
	private final PinbabelCliRenderer renderer;

	public PinbabelDiscoveryShellCommands(
		RecommendXStockInfluencersUseCase recommendUseCase,
		PinbabelCliRenderer renderer
	) {
		this.recommendUseCase = recommendUseCase;
		this.renderer = renderer;
	}

	@ShellMethod(
		key = "pinbabel-recommend-x-accounts",
		value = "Show curated X stock-related accounts without external API calls"
	)
	public String recommendXAccounts() {
		return renderer.renderRecommendations(recommendUseCase.recommend());
	}
}
