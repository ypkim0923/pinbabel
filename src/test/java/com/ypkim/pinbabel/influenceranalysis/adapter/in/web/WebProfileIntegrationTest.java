package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
	"embabel.agent.shell.interactive.enabled=false",
	"embabel.agent.shell.interactive.history-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"fixture", "web"})
class WebProfileIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rendersTheDirectoryShellAndTenProfileFragmentWithoutSecrets() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("인플루언서 조회")));

		mockMvc.perform(get("/influencers").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("CURATED DIRECTORY")))
			.andExpect(content().string(Matchers.containsString("@aleabitoreddit")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("X_BEARER_TOKEN"))));
	}

	@Test
	void rendersAZeroCostFixtureDetailWithFourSentimentSections() throws Exception {
		mockMvc.perform(get("/influencers/growth-lab"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("Pin Growth Lab")))
			.andExpect(content().string(Matchers.containsString("긍정")))
			.andExpect(content().string(Matchers.containsString("부정")))
			.andExpect(content().string(Matchers.containsString("중립")))
			.andExpect(content().string(Matchers.containsString("판단 불가")))
			.andExpect(content().string(Matchers.containsString("0 / 0")))
			.andExpect(content().string(Matchers.not(Matchers.containsString(";jsessionid="))))
			.andExpect(content().string(Matchers.not(Matchers.containsString("https://x.com/pin_growthlab"))));
	}

	@Test
	void keepsSerenityVisibleButDoesNotOfferLiveExecutionWithoutLiveProfiles() throws Exception {
		mockMvc.perform(get("/influencers/serenity"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("Serenity")))
			.andExpect(content().string(Matchers.containsString("실행 설정이 준비되면")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("name=\"executionToken\""))));
	}

	@Test
	void rendersSafeNotFoundWithoutInternalExceptionDetails() throws Exception {
		mockMvc.perform(get("/influencers/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(content().string(Matchers.containsString("요청한 인플루언서 프로필을 찾을 수 없습니다.")))
			.andExpect(content().string(Matchers.containsString("PIN-IAN-0104")))
			.andExpect(content().string(Matchers.not(Matchers.containsString("IllegalArgumentException"))));
	}
}
