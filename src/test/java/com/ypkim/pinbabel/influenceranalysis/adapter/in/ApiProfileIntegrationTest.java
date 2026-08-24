package com.ypkim.pinbabel.influenceranalysis.adapter.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = {
	"embabel.agent.shell.interactive.enabled=false",
	"embabel.agent.shell.interactive.history-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"fixture", "api"})
class ApiProfileIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void exposesEmbabelCompatibleA2AAgentCard() throws Exception {
		mockMvc.perform(get("/a2a/.well-known/agent.json"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Pinbabel"))
			.andExpect(jsonPath("$.protocolVersion").value("0.3.0"))
			.andExpect(jsonPath("$.capabilities.streaming").value(false));
	}

	@Test
	void rejectsInvalidRestAndA2UiInputsWithTheSharedBoundaryPolicy() throws Exception {
		mockMvc.perform(post("/api/v1/influencer-analyses")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"instruction\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/a2ui/v0.9/analyses")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"instruction\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsOversizedBodiesBeforeJsonBinding() throws Exception {
		var oversized = "{\"instruction\":\"" + "x".repeat(17_000) + "\"}";
		mockMvc.perform(post("/api/v1/influencer-analyses")
				.contentType(MediaType.APPLICATION_JSON)
				.content(oversized))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"));
	}

	@Test
	void servesTheStaticOpenApiContract() throws Exception {
		mockMvc.perform(get("/openapi/pinbabel-v1.yaml"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/a2ui/v0.9/analyses")));
	}
}
