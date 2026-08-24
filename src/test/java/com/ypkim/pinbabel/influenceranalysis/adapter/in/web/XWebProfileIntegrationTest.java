package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"X_BEARER_TOKEN=test-token",
	"embabel.agent.shell.interactive.enabled=false",
	"embabel.agent.shell.interactive.history-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"fixture", "x", "web"})
class XWebProfileIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void keepsLiveExecutionDisabledWithoutTheLlmProfile() throws Exception {
		mockMvc.perform(get("/influencers/serenity"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("실행 설정이 준비되면")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("name=\"executionToken\""))));

		mockMvc.perform(post("/influencers/serenity/analyses").param("executionToken", "forged"))
			.andExpect(status().isForbidden())
			.andExpect(content().string(Matchers.containsString("PIN-IAN-0108")));
	}
}
