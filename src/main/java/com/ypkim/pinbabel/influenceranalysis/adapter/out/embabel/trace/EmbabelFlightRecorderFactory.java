package com.ypkim.pinbabel.influenceranalysis.adapter.out.embabel.trace;

import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.api.event.AbstractAgentProcessEvent;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorder;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunFlightRecorderFactory;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@SecondaryAdapter
public class EmbabelFlightRecorderFactory implements AnalysisRunFlightRecorderFactory, AgenticEventListener {

	private static final Logger log = LoggerFactory.getLogger(EmbabelFlightRecorderFactory.class);

	private final AnalysisRunStore store;
	private final int maxEvents;
	private final ConcurrentMap<String, EmbabelFlightRecorderSession> sessions = new ConcurrentHashMap<>();

	public EmbabelFlightRecorderFactory(
		AnalysisRunStore store,
		@Value("${pinbabel.analysis-run.max-events:1000}") int maxEvents
	) {
		if (maxEvents < 2) {
			throw new IllegalArgumentException("Analysis trace max events must be at least 2");
		}
		this.store = store;
		this.maxEvents = maxEvents;
	}

	@Override
	public AnalysisRunFlightRecorder create(AnalysisRunId runId) {
		var session = new EmbabelFlightRecorderSession(runId, store, new SafeEmbabelEventMapper(), maxEvents);
		var previous = sessions.putIfAbsent(runId.value(), session);
		if (previous != null) {
			throw new IllegalStateException("Analysis run recorder already exists");
		}
		session.onClose(() -> sessions.remove(runId.value(), session));
		return session;
	}

	@Override
	public void onProcessEvent(AgentProcessEvent event) {
		try {
			if (!(event instanceof AbstractAgentProcessEvent processEvent)) {
				return;
			}
			var contextId = processEvent.getAgentProcess().getProcessOptions().getContextIdString();
			if (contextId == null) {
				return;
			}
			var session = sessions.get(contextId);
			if (session != null) {
				session.onProcessEvent(event);
			}
		}
		catch (RuntimeException exception) {
			log.warn("Analysis trace event routing failed");
		}
	}
}
