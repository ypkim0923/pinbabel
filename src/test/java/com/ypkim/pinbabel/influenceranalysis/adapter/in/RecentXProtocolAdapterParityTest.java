package com.ypkim.pinbabel.influenceranalysis.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2a.PinbabelA2AAgentCardHandler;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui.A2UiRecentXAnalysisController;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui.A2UiRecentXAnalysisRequest;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.a2ui.A2UiRecentXSnapshotRenderer;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.rest.RecentXAnalysisCreateRequest;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.rest.RecentXAnalysisRestController;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.QueryAnalysisCapabilitiesUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.SubmitInfluencerAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.QueryAnalysisRunsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.analysisrun.dto.AnalysisRunSummaryResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.QueryRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.SubmitRecentXAnalysisUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentMentionedCompaniesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.discovery.dto.RecentXAnalysisDetailResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisCapabilitiesResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalysisSubmissionResource;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.SubmitInfluencerAnalysisCommand;
import io.a2a.spec.DataPart;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RecentXProtocolAdapterParityTest {
	private static final String RUN_ID = "0198d1bb-99e0-7000-8000-000000000201";
	private static final String CORRELATION_ID = "0198d1bb-99e0-7000-8000-000000000202";

	@Test
	void restA2aAndA2uiSubmitTheSameAccountToOnePort() {
		var ports = new Ports();
		var rest = new RecentXAnalysisRestController(ports, ports);
		var a2ui = new A2UiRecentXAnalysisController(ports, ports, new A2UiRecentXSnapshotRenderer(new ObjectMapper()));
		var a2a = new PinbabelA2AAgentCardHandler(ports, ports, ports, ports, ports, "http://127.0.0.1:8080");

		var restResponse = rest.create(new RecentXAnalysisCreateRequest("@aleabitoreddit"));
		var a2uiResponse = a2ui.create(new A2UiRecentXAnalysisRequest("@aleabitoreddit"));
		var message = new Message.Builder().role(Message.Role.USER)
			.parts(new DataPart(Map.of("operation", "analyzeRecentXCompanies", "account", "@aleabitoreddit")))
			.messageId("message-1").build();
		var response = (SendMessageResponse) a2a.handleJsonRpc(
			new SendMessageRequest("request-1", new MessageSendParams(message, null, null))
		);

		assertThat(ports.accounts).containsExactly("@aleabitoreddit", "@aleabitoreddit", "@aleabitoreddit");
		assertThat(restResponse.getStatusCode().value()).isEqualTo(202);
		assertThat(a2uiResponse.getBody()).contains(RUN_ID).contains(CORRELATION_ID);
		assertThat(((Task) response.getResult()).getId()).isEqualTo(RUN_ID);
		assertThat(a2a.agentCard("http", "localhost", 8080).skills())
			.extracting(io.a2a.spec.AgentSkill::id).contains("analyze-recent-x-companies");
	}

	@Test
	void a2aCompletedTaskContainsTheRecentResultArtifact() {
		var ports = new Ports(true);
		var a2a = new PinbabelA2AAgentCardHandler(ports, ports, ports, ports, ports, "http://127.0.0.1:8080");

		var response = (GetTaskResponse) a2a.handleJsonRpc(
			new GetTaskRequest("request-2", new TaskQueryParams(RUN_ID, 0))
		);
		var task = response.getResult();
		var artifact = (DataPart) task.getArtifacts().getFirst().parts().getFirst();

		assertThat(task.getStatus().state()).isEqualTo(io.a2a.spec.TaskState.COMPLETED);
		assertThat(artifact.getData())
			.containsEntry("account", "@aleabitoreddit")
			.containsEntry("xApiRequestBudget", 2)
			.containsEntry("llmCallBudget", 1);
	}

	private static final class Ports implements SubmitRecentXAnalysisUseCase, QueryRecentXAnalysisUseCase,
		SubmitInfluencerAnalysisUseCase, QueryAnalysisRunsUseCase, QueryAnalysisCapabilitiesUseCase {
		private final List<String> accounts = new java.util.ArrayList<>();
		private final boolean completed;

		private Ports() { this(false); }
		private Ports(boolean completed) { this.completed = completed; }

		@Override public AnalysisSubmissionResource submit(String account) {
			accounts.add(account);
			return new AnalysisSubmissionResource(RUN_ID, CORRELATION_ID, "CREATED", Instant.EPOCH, null, null);
		}
		@Override public Optional<RecentXAnalysisDetailResource> findRecentRun(AnalysisRunId runId) {
			return Optional.of(new RecentXAnalysisDetailResource(
				RUN_ID, CORRELATION_ID, completed ? "COMPLETED" : "CREATED", Instant.EPOCH,
				completed ? Instant.EPOCH : null, completed ? Instant.EPOCH : null, completed ? 0L : null,
				completed ? "RECENT_X_ANALYSIS_COMPLETED" : null, completed ? "done" : null,
				new RecentMentionedCompaniesResource(
					completed ? "COMPLETED" : "CREATED", completed ? "done" : "queued", "@aleabitoreddit", 0, true, true, false,
					0, 0, 2, 1, 0, List.of(), List.of(), "not advice"
				)
			));
		}
		@Override public AnalysisSubmissionResource submit(SubmitInfluencerAnalysisCommand command) { throw new UnsupportedOperationException(); }
		@Override public List<AnalysisRunSummaryResource> recentRuns() { return List.of(); }
		@Override public Optional<AnalysisRunDetailResource> findRun(com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId runId) { return Optional.empty(); }
		@Override public AnalysisCapabilitiesResource capabilities() { return new AnalysisCapabilitiesResource(Set.of("x")); }
	}
}
