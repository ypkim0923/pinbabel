package com.ypkim.pinbabel.influenceranalysis.adapter.out.embabel.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.embabel.agent.api.event.AgentProcessCreationEvent;
import com.embabel.agent.api.event.LlmInvocationEvent;
import com.embabel.agent.core.LlmInvocation;
import com.embabel.agent.core.Usage;
import com.embabel.common.ai.model.LlmMetadata;
import com.embabel.common.ai.model.PricingModel;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorder;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbabelFlightRecorderSessionTest {

	private static final AnalysisRunId RUN_ID = new AnalysisRunId("0198d1bb-99e0-7000-8000-000000000001");

	@Test
	void storesOnlySafeMetadataAndOrdersEvents() {
		var store = new RecordingStore();
		var session = session(store, 10);

		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:00Z")));
		session.onProcessEvent(llmInvocation("secret-model", "provider", 12, 7, true));

		assertThat(store.events).extracting(AnalysisTraceEvent::sequence).containsExactly(1L, 2L);
		assertThat(store.events.get(1).eventType()).isEqualTo("LLM_INVOKED");
		assertThat(store.events.get(1).modelName()).isEqualTo("secret-model");
		assertThat(session.processId()).isEqualTo("process-1");
		assertThat(session.metrics().promptTokens()).isEqualTo(12L);
		assertThat(session.metrics().completionTokens()).isEqualTo(7L);
		assertThat(session.metrics().costUsd()).isNotNull();
	}

	@Test
	void keepsUnavailableUsageAndCostNull() {
		var store = new RecordingStore();
		var session = session(store, 10);

		session.onProcessEvent(llmInvocation("gemini", "provider", null, null, false));

		assertThat(session.metrics().promptTokens()).isNull();
		assertThat(session.metrics().completionTokens()).isNull();
		assertThat(session.metrics().costUsd()).isNull();
	}

	@Test
	void writesSingleTruncationMarkerAndStopsAtCap() {
		var store = new RecordingStore();
		var session = session(store, 3);

		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:00Z")));
		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:01Z")));
		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:02Z")));
		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:03Z")));

		assertThat(store.events).extracting(AnalysisTraceEvent::eventType)
			.containsExactly("PROCESS_CREATED", "PROCESS_CREATED", "TRACE_TRUNCATED");
		assertThat(session.traceAvailable()).isFalse();
		assertThat(session.warningCode()).isEqualTo(AnalysisRunFlightRecorder.TRUNCATED_WARNING);
	}

	@Test
	void stopsAppendingAfterStorageFailure() {
		var store = new RecordingStore();
		store.fail = true;
		var session = session(store, 10);

		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:00Z")));
		store.fail = false;
		session.onProcessEvent(processCreated("process-1", Instant.parse("2026-08-24T01:00:01Z")));

		assertThat(store.appendAttempts).isEqualTo(1);
		assertThat(session.traceAvailable()).isFalse();
		assertThat(session.warningCode()).isEqualTo(AnalysisRunFlightRecorder.STORAGE_WARNING);
	}

	@Test
	void isolatesMalformedEmbabelEventFromTheAnalysis() {
		var session = session(new RecordingStore(), 10);
		var malformed = mock(LlmInvocationEvent.class);
		when(malformed.getInvocation()).thenReturn(null);

		assertThatCode(() -> session.onProcessEvent(malformed)).doesNotThrowAnyException();
		assertThat(session.traceAvailable()).isFalse();
		assertThat(session.warningCode()).isEqualTo(AnalysisRunFlightRecorder.LISTENER_WARNING);
	}

	private static EmbabelFlightRecorderSession session(RecordingStore store, int maxEvents) {
		return new EmbabelFlightRecorderSession(RUN_ID, store, new SafeEmbabelEventMapper(), maxEvents);
	}

	private static AgentProcessCreationEvent processCreated(String processId, Instant occurredAt) {
		var event = mock(AgentProcessCreationEvent.class);
		when(event.getProcessId()).thenReturn(processId);
		when(event.getTimestamp()).thenReturn(occurredAt);
		return event;
	}

	private static LlmInvocationEvent llmInvocation(
		String model,
		String provider,
		Integer promptTokens,
		Integer completionTokens,
		boolean priced
	) {
		var metadata = mock(LlmMetadata.class);
		when(metadata.getName()).thenReturn(model);
		when(metadata.getProvider()).thenReturn(provider);
		if (priced) {
			var pricing = mock(PricingModel.class);
			when(pricing.costOf(promptTokens, completionTokens)).thenReturn(0.0012);
			when(metadata.getPricingModel()).thenReturn(pricing);
		}
		var invocation = new LlmInvocation(
			metadata,
			new Usage(promptTokens, completionTokens, null),
			null,
			Instant.parse("2026-08-24T01:00:00Z"),
			Duration.ofMillis(120)
		);
		var event = mock(LlmInvocationEvent.class);
		when(event.getInvocation()).thenReturn(invocation);
		when(event.getProcessId()).thenReturn("process-1");
		when(event.getTimestamp()).thenReturn(invocation.getTimestamp());
		return event;
	}

	private static final class RecordingStore implements AnalysisRunStore {

		private final List<AnalysisTraceEvent> events = new ArrayList<>();
		private boolean fail;
		private int appendAttempts;

		@Override
		public void save(AnalysisRun run, InfluencerAnalysisReport report) {
		}

		@Override
		public void append(AnalysisRunId runId, AnalysisTraceEvent event) {
			appendAttempts++;
			if (fail) {
				throw new IllegalStateException("database unavailable");
			}
			events.add(event);
		}

		@Override
		public List<StoredAnalysisRunSummary> findLatest(int limit) {
			return List.of();
		}

		@Override
		public Optional<StoredAnalysisRunDetail> findById(AnalysisRunId runId) {
			return Optional.empty();
		}
	}
}
