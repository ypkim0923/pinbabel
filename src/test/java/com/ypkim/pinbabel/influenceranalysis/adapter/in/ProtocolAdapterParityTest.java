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
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.InfluencerAnalysisReportResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisCapabilitiesResource;
import io.a2a.spec.DataPart;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TextPart;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProtocolAdapterParityTest {
	private static final String RUN_ID = "0198d1bb-99e0-7000-8000-000000000001";
	private static final String CORRELATION_ID = "0198d1bb-99e0-7000-8000-000000000002";
	private static final String INSTRUCTION =
		"x의 XDevelopers를 2026-08-22부터 2026-08-24까지 UTC NASDAQ 분석";

	@Test
	void allAdaptersSubmitTheSameApplicationCommandAndExposeTheSameIdentifiers() {
		var ports = new RecordingPorts();
		var rest = new InfluencerAnalysisRestController(ports, ports);
		var a2ui = new A2UiAnalysisController(ports, ports, new A2UiSnapshotRenderer(new ObjectMapper()));
		var a2a = new PinbabelA2AAgentCardHandler(
			ports, ports, () -> new AnalysisCapabilitiesResource(Set.of("fixture-social")),
			"http://127.0.0.1:8080"
		);

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

	@Test
	void a2aCompletedTaskOmitsUnknownInstrumentIdentifiersFromEvidence() {
		var ports = new RecordingPorts(completedRunWithUnknownInstrumentEvidence());
		var a2a = new PinbabelA2AAgentCardHandler(
			ports, ports, () -> new AnalysisCapabilitiesResource(Set.of("fixture-social")),
			"http://127.0.0.1:8080"
		);

		var response = (GetTaskResponse) a2a.handleJsonRpc(
			new GetTaskRequest("request-2", new TaskQueryParams(RUN_ID, 0))
		);
		var task = response.getResult();
		var report = (DataPart) task.getArtifacts().getFirst().parts().getFirst();
		@SuppressWarnings("unchecked")
		var evidence = (List<java.util.Map<String, Object>>) report.getData().get("evidence");

		assertThat(task.getStatus().state()).isEqualTo(io.a2a.spec.TaskState.COMPLETED);
		assertThat(evidence.getFirst())
			.containsEntry("sentiment", "UNCERTAIN")
			.doesNotContainKeys("instrumentId", "ticker");
	}

	@Test
	void a2aAgentCardAdvertisesOnlyTheActiveCollectionPlatform() {
		var ports = new RecordingPorts();
		var fixtureCard = new PinbabelA2AAgentCardHandler(
			ports, ports, () -> new AnalysisCapabilitiesResource(Set.of("fixture-social")),
			"http://127.0.0.1:8080"
		).agentCard("http", "127.0.0.1", 8080);
		var xCard = new PinbabelA2AAgentCardHandler(
			ports, ports, () -> new AnalysisCapabilitiesResource(Set.of("x")),
			"http://127.0.0.1:8080"
		).agentCard("http", "127.0.0.1", 8080);

		assertThat(fixtureCard.skills().getFirst().examples().getFirst()).contains("fixture-social").doesNotContain("x의");
		assertThat(xCard.skills().getFirst().examples().getFirst()).contains("x의").doesNotContain("fixture-social");
	}

	private static AnalysisRunDetailResource completedRunWithUnknownInstrumentEvidence() {
		var report = new InfluencerAnalysisReportResource(
			"fixture-social",
			"market_maven",
			new InfluencerAnalysisReportResource.PeriodResource(Instant.EPOCH, Instant.EPOCH.plusSeconds(86_400), "UTC"),
			List.of(),
			List.of(new InfluencerAnalysisReportResource.EvidenceResource(
				"post-injection", "fixture-social", "market_maven", Instant.EPOCH,
				"https://social.example/posts/post-injection", "fixture", null, null,
				"UNCERTAIN", "untrusted content", "No canonical instrument"
			)),
			List.of(),
			"Not investment advice"
		);
		return new AnalysisRunDetailResource(
			RUN_ID, CORRELATION_ID, "COMPLETED", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, 0L, true,
			null, "ANALYSIS_COMPLETED", "Analysis completed",
			new AnalysisRunMetricsResource(null, null, null, List.of()), report, List.of()
		);
	}

	private static final class RecordingPorts implements SubmitInfluencerAnalysisUseCase, QueryAnalysisRunsUseCase {
		private final List<String> instructions = new java.util.ArrayList<>();
		private final AnalysisRunDetailResource detail;

		private RecordingPorts() {
			this(new AnalysisRunDetailResource(
				RUN_ID, CORRELATION_ID, "CREATED", Instant.EPOCH, null, null, null, false,
				null, null, null, new AnalysisRunMetricsResource(null, null, null, List.of()), null, List.of()
			));
		}

		private RecordingPorts(AnalysisRunDetailResource detail) {
			this.detail = detail;
		}

		@Override
		public AnalysisSubmissionResource submit(SubmitInfluencerAnalysisCommand command) {
			instructions.add(command.instruction());
			return new AnalysisSubmissionResource(RUN_ID, CORRELATION_ID, "CREATED", Instant.EPOCH, null, null);
		}

		@Override
		public List<AnalysisRunSummaryResource> recentRuns() { return List.of(); }

		@Override
		public Optional<AnalysisRunDetailResource> findRun(AnalysisRunId runId) {
			return Optional.of(detail);
		}
	}
}
