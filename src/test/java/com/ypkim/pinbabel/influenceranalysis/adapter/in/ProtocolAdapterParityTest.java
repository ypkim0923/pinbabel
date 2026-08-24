package com.ypkim.pinbabel.influenceranalysis.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2a.PinbabelA2AAgentCardHandler;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui.A2UiAnalysisController;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui.A2UiAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui.A2UiSnapshotRenderer;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.rest.InfluencerAnalysisCreateRequest;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.rest.InfluencerAnalysisRestController;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunMetricsResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProtocolAdapterParityTest {
	private static final String RUN_ID = "0198d1bb-99e0-7000-8000-000000000001";
	private static final String CORRELATION_ID = "0198d1bb-99e0-7000-8000-000000000002";
	private static final String INSTRUCTION = "fixture-social의 market_maven을 2025-01-01부터 2025-01-04까지 UTC NASDAQ 분석";

	@Test
	void allAdaptersSubmitTheSameApplicationCommandAndExposeTheSameIdentifiers() {
		var ports = new RecordingPorts();
		var rest = new InfluencerAnalysisRestController(ports, ports);
		var a2ui = new A2UiAnalysisController(ports, ports, new A2UiSnapshotRenderer(new ObjectMapper()));
		var a2a = new PinbabelA2AAgentCardHandler(ports, ports, "http://127.0.0.1:8080");

		var restResponse = rest.create(new InfluencerAnalysisCreateRequest(INSTRUCTION));
		var a2uiResponse = a2ui.create(new A2UiAnalysisRequest(INSTRUCTION));
		var message = new Message.Builder().role(Message.Role.USER).parts(new TextPart(INSTRUCTION)).messageId("message-1").build();
		var a2aResponse = (SendMessageResponse) a2a.handleJsonRpc(new SendMessageRequest("request-1", new MessageSendParams(message, null, null)));
		var a2aTask = (Task) a2aResponse.getResult();

		assertThat(ports.instructions).containsExactly(INSTRUCTION, INSTRUCTION, INSTRUCTION);
		assertThat(restResponse.getStatusCode().value()).isEqualTo(202);
		assertThat(restResponse.getBody().runId()).isEqualTo(RUN_ID);
		assertThat(a2uiResponse.getBody()).contains(RUN_ID).contains(CORRELATION_ID);
		assertThat(a2aTask.getId()).isEqualTo(RUN_ID);
		assertThat(a2aTask.getContextId()).isEqualTo(CORRELATION_ID);
	}

	private static final class RecordingPorts implements SubmitInfluencerAnalysisUseCase, QueryAnalysisRunsUseCase {
		private final List<String> instructions = new java.util.ArrayList<>();

		@Override
		public AnalysisSubmissionResource submit(SubmitInfluencerAnalysisCommand command) {
			instructions.add(command.instruction());
			return new AnalysisSubmissionResource(RUN_ID, CORRELATION_ID, "CREATED", Instant.EPOCH, null, null);
		}

		@Override
		public List<AnalysisRunSummaryResource> recentRuns() { return List.of(); }

		@Override
		public Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId) {
			return Optional.of(new AnalysisRunDetailResource(
				RUN_ID, CORRELATION_ID, "CREATED", Instant.EPOCH, null, null, null, false,
				null, null, null, new AnalysisRunMetricsResource(null, null, null, List.of()), null, List.of()
			));
		}
	}
}
