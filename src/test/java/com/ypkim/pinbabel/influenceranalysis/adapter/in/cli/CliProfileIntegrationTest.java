package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.Chatbot;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.cli.chat.PinbabelChatbot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	"embabel.agent.shell.interactive.enabled=false",
	"embabel.agent.shell.interactive.history-enabled=false"
})
@ActiveProfiles({"fixture", "cli"})
class CliProfileIntegrationTest {

	@Autowired
	private AgentPlatform agentPlatform;

	@Autowired
	private PinbabelShellCommands shellCommands;

	@Autowired
	private PinbabelRunShellCommands runShellCommands;

	@Autowired
	private PinbabelEvaluationShellCommands evaluationShellCommands;

	@Autowired
	private Chatbot chatbot;

	@Test
	void startsWithoutApiKeyAndReplacesGenericChatbot() {
		assertThat(shellCommands).isNotNull();
		assertThat(runShellCommands).isNotNull();
		assertThat(evaluationShellCommands).isNotNull();
		assertThat(chatbot).isInstanceOf(PinbabelChatbot.class);
		assertThat(agentPlatform.agents())
			.anyMatch(agent -> agent.getDescription().contains("stock influencer"));
	}
}
