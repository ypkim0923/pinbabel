package com.ypkim.pinbabel.influenceranalysis.adapter.out.embabel.trace;

import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.LlmInvocationEvent;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
public final class EmbabelFlightRecorderSession implements AnalysisRunFlightRecorder {

	private static final Logger log = LoggerFactory.getLogger(EmbabelFlightRecorderSession.class);

	private final AnalysisRunId runId;
	private final AnalysisRunStore store;
	private final SafeEmbabelEventMapper mapper;
	private final int maxEvents;
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);
	private final AtomicBoolean traceAvailable = new AtomicBoolean(true);
	private final AtomicReference<String> warningCode = new AtomicReference<>();
	private final AtomicReference<String> processId = new AtomicReference<>();
	private final AtomicLong invocationCount = new AtomicLong();
	private final AtomicLong promptTokens = new AtomicLong();
	private final AtomicLong completionTokens = new AtomicLong();
	private final AtomicReference<BigDecimal> costUsd = new AtomicReference<>(BigDecimal.ZERO);
	private final AtomicBoolean promptTokensComplete = new AtomicBoolean(true);
	private final AtomicBoolean completionTokensComplete = new AtomicBoolean(true);
	private final AtomicBoolean costComplete = new AtomicBoolean(true);
	private final ConcurrentSkipListSet<String> models = new ConcurrentSkipListSet<>();
	private final AtomicReference<Runnable> closeAction = new AtomicReference<>(() -> { });

	EmbabelFlightRecorderSession(
		AnalysisRunId runId,
		AnalysisRunStore store,
		SafeEmbabelEventMapper mapper,
		int maxEvents
	) {
		this.runId = runId;
		this.store = store;
		this.mapper = mapper;
		this.maxEvents = maxEvents;
	}

	void onProcessEvent(AgentProcessEvent event) {
		try {
			recordEvent(event);
		}
		catch (RuntimeException exception) {
			degrade(LISTENER_WARNING);
			log.warn("Analysis trace event mapping failed: runId={}", runId.value());
		}
	}

	private void recordEvent(AgentProcessEvent event) {
		processId.compareAndSet(null, event.getProcessId());
		if (event instanceof LlmInvocationEvent invocationEvent) {
			recordMetrics(invocationEvent);
		}
		if (!acceptingEvents.get()) {
			return;
		}

		var nextSequence = sequence.get() + 1;
		var mapped = mapper.map(event, nextSequence);
		if (mapped.isEmpty()) {
			return;
		}
		if (nextSequence >= maxEvents) {
			appendTruncationMarker(nextSequence, event.getTimestamp(), event.getProcessId());
			return;
		}
		append(mapped.orElseThrow());
	}

	private void append(AnalysisTraceEvent event) {
		if (!sequence.compareAndSet(event.sequence() - 1, event.sequence())) {
			event = new AnalysisTraceEvent(
				sequence.incrementAndGet(), event.eventType(), event.occurredAt(), event.processId(),
				event.actionName(), event.toolName(), event.modelName(), event.providerName(),
				event.durationMs(), event.successful()
			);
		}
		try {
			store.append(runId, event);
		}
		catch (RuntimeException exception) {
			degrade(STORAGE_WARNING);
			log.warn("Analysis trace listener failed: runId={}", runId.value());
		}
	}

	private void appendTruncationMarker(long nextSequence, Instant occurredAt, String embabelProcessId) {
		if (!acceptingEvents.compareAndSet(true, false)) {
			return;
		}
		traceAvailable.set(false);
		warningCode.compareAndSet(null, TRUNCATED_WARNING);
		append(new AnalysisTraceEvent(
			nextSequence, "TRACE_TRUNCATED", occurredAt, embabelProcessId,
			null, null, null, null, null, false
		));
	}

	private void degrade(String warning) {
		traceAvailable.set(false);
		warningCode.compareAndSet(null, warning);
		acceptingEvents.set(false);
	}

	private void recordMetrics(LlmInvocationEvent event) {
		var invocation = event.getInvocation();
		invocationCount.incrementAndGet();
		models.add(invocation.getLlmMetadata().getName());
		var usage = invocation.getUsage();
		if (usage.getPromptTokens() == null) {
			promptTokensComplete.set(false);
		}
		else {
			promptTokens.addAndGet(usage.getPromptTokens());
		}
		if (usage.getCompletionTokens() == null) {
			completionTokensComplete.set(false);
		}
		else {
			completionTokens.addAndGet(usage.getCompletionTokens());
		}
		var pricing = invocation.getLlmMetadata().getPricingModel();
		if (pricing == null || usage.getPromptTokens() == null || usage.getCompletionTokens() == null) {
			costComplete.set(false);
		}
		else {
			costUsd.updateAndGet(current -> current.add(BigDecimal.valueOf(
				pricing.costOf(usage.getPromptTokens(), usage.getCompletionTokens())
			)));
		}
	}

	public boolean traceAvailable() {
		return traceAvailable.get();
	}

	public String warningCode() {
		return warningCode.get();
	}

	public String processId() {
		return processId.get();
	}

	public AnalysisRunMetrics metrics() {
		if (invocationCount.get() == 0) {
			return AnalysisRunMetrics.EMPTY;
		}
		return new AnalysisRunMetrics(
			promptTokensComplete.get() ? promptTokens.get() : null,
			completionTokensComplete.get() ? completionTokens.get() : null,
			costComplete.get() ? costUsd.get() : null,
			models.stream().toList()
		);
	}

	void onClose(Runnable action) {
		closeAction.set(action);
	}

	@Override
	public void close() {
		closeAction.getAndSet(() -> { }).run();
	}
}
