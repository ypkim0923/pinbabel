package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.AnalyzeRecentXStockInfluencerUseCase;
import java.util.function.Consumer;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Profile("fixture & x & cli")
@PrimaryAdapter
public class PinbabelRecentDiscoveryShellCommands {

	private final AnalyzeRecentXStockInfluencerUseCase useCase;
	private final PinbabelCliRenderer renderer;
	private final Consumer<String> progressWriter;

	@Autowired
	public PinbabelRecentDiscoveryShellCommands(
		AnalyzeRecentXStockInfluencerUseCase useCase,
		PinbabelCliRenderer renderer,
		Terminal terminal
	) {
		this(useCase, renderer, message -> {
			terminal.writer().println(message);
			terminal.writer().flush();
		});
	}

	PinbabelRecentDiscoveryShellCommands(
		AnalyzeRecentXStockInfluencerUseCase useCase,
		PinbabelCliRenderer renderer
	) {
		this(useCase, renderer, ignored -> { });
	}

	PinbabelRecentDiscoveryShellCommands(
		AnalyzeRecentXStockInfluencerUseCase useCase,
		PinbabelCliRenderer renderer,
		Consumer<String> progressWriter
	) {
		this.useCase = useCase;
		this.renderer = renderer;
		this.progressWriter = progressWriter;
	}

	@ShellMethod(
		key = "pinbabel-x-companies",
		value = "List companies in an X account's ten recent non-reply, non-repost posts"
	)
	public String mentionedCompanies(
		@ShellOption(value = "--account", help = "Exact X username or @handle") String account
	) {
		progressWriter.accept("X 최근 게시물 수집 및 회사 분석을 시작합니다. (X API 최대 2회, LLM 최대 1회, timeout 60초)");
		var result = useCase.mentionedCompanies(account);
		progressWriter.accept(completionMessage(result.status()));
		return renderer.renderRecentCompanies(result);
	}

	@ShellMethod(
		key = "pinbabel-x-sentiment",
		value = "List positive and negative companies in an X account's ten recent posts"
	)
	public String companySentiment(
		@ShellOption(value = "--account", help = "Exact X username or @handle") String account
	) {
		progressWriter.accept("X 최근 게시물 수집 및 sentiment 분석을 시작합니다. (X API 최대 2회, LLM 최대 1회, timeout 60초)");
		var result = useCase.companySentiment(account);
		progressWriter.accept(completionMessage(result.status()));
		return renderer.renderRecentCompanySentiment(result);
	}

	private String completionMessage(String status) {
		return "COMPLETED".equals(status) ? "분석이 완료되었습니다." : "분석이 실패했습니다.";
	}
}
