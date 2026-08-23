package com.ypkim.pinbabel.influenceranalysis.adapter.out.embabel.trace;

import com.embabel.agent.api.event.ActionExecutionResultEvent;
import com.embabel.agent.api.event.ActionExecutionStartEvent;
import com.embabel.agent.api.event.AgentProcessCompletedEvent;
import com.embabel.agent.api.event.AgentProcessCreationEvent;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgentProcessFailedEvent;
import com.embabel.agent.api.event.AgentProcessPausedEvent;
import com.embabel.agent.api.event.AgentProcessPlanFormulatedEvent;
import com.embabel.agent.api.event.AgentProcessReadyToPlanEvent;
import com.embabel.agent.api.event.AgentProcessStuckEvent;
import com.embabel.agent.api.event.AgentProcessWaitingEvent;
import com.embabel.agent.api.event.GoalAchievedEvent;
import com.embabel.agent.api.event.LlmInvocationEvent;
import com.embabel.agent.api.event.LlmRequestEvent;
import com.embabel.agent.api.event.LlmResponseEvent;
import com.embabel.agent.api.event.ReplanRequestedEvent;
import com.embabel.agent.api.event.ToolCallRequestEvent;
import com.embabel.agent.api.event.ToolCallResponseEvent;
import com.embabel.agent.api.event.ToolLoopCompletedEvent;
import com.embabel.agent.api.event.ToolLoopStartEvent;
import com.embabel.agent.core.ActionStatusCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import java.time.Duration;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
final class SafeEmbabelEventMapper {

	Optional<AnalysisTraceEvent> map(AgentProcessEvent event, long sequence) {
		var metadata = metadata(event);
		if (metadata == null) {
			return Optional.empty();
		}
		return Optional.of(new AnalysisTraceEvent(
			sequence,
			metadata.type(),
			event.getTimestamp(),
			bounded(event.getProcessId()),
			bounded(metadata.actionName()),
			bounded(metadata.toolName()),
			bounded(metadata.modelName()),
			bounded(metadata.providerName()),
			toMillis(metadata.duration()),
			metadata.successful()
		));
	}

	private EventMetadata metadata(AgentProcessEvent event) {
		if (event instanceof AgentProcessCreationEvent) {
			return event("PROCESS_CREATED");
		}
		if (event instanceof AgentProcessReadyToPlanEvent) {
			return event("READY_TO_PLAN");
		}
		if (event instanceof AgentProcessPlanFormulatedEvent) {
			return event("PLAN_FORMULATED");
		}
		if (event instanceof ReplanRequestedEvent) {
			return event("REPLAN_REQUESTED");
		}
		if (event instanceof GoalAchievedEvent achieved) {
			return new EventMetadata("GOAL_ACHIEVED", achieved.getGoal().getName(), null, null, null, null, true);
		}
		if (event instanceof ActionExecutionStartEvent started) {
			return action("ACTION_STARTED", started.getAction().getName(), null, null);
		}
		if (event instanceof ActionExecutionResultEvent result) {
			var successful = result.getActionStatus().getStatus() == ActionStatusCode.SUCCEEDED;
			return action("ACTION_COMPLETED", result.getAction().getName(), result.getRunningTime(), successful);
		}
		if (event instanceof ToolLoopStartEvent loop) {
			return action("TOOL_LOOP_STARTED", actionName(loop.getAction()), null, null);
		}
		if (event instanceof ToolLoopCompletedEvent loop) {
			return action("TOOL_LOOP_COMPLETED", actionName(loop.getAction()), loop.getRunningTime(), true);
		}
		if (event instanceof ToolCallRequestEvent request) {
			return new EventMetadata(
				"TOOL_CALL_REQUESTED", actionName(request.getAction()), request.getTool(), null, null, null, null
			);
		}
		if (event instanceof ToolCallResponseEvent response) {
			var request = response.getRequest();
			return new EventMetadata(
				"TOOL_CALL_COMPLETED", actionName(request.getAction()), request.getTool(), null, null,
				response.getRunningTime(), null
			);
		}
		if (event instanceof LlmRequestEvent<?> request) {
			return llm("LLM_REQUESTED", request.getLlmMetadata().getName(), request.getLlmMetadata().getProvider(), null, null);
		}
		if (event instanceof LlmResponseEvent<?> response) {
			var metadata = response.getRequest().getLlmMetadata();
			return llm("LLM_RESPONDED", metadata.getName(), metadata.getProvider(), response.getRunningTime(), true);
		}
		if (event instanceof LlmInvocationEvent invocation) {
			var metadata = invocation.getInvocation().getLlmMetadata();
			return llm(
				"LLM_INVOKED", metadata.getName(), metadata.getProvider(),
				invocation.getInvocation().getRunningTime(), true
			);
		}
		if (event instanceof AgentProcessCompletedEvent) {
			return terminal("PROCESS_COMPLETED", true);
		}
		if (event instanceof AgentProcessFailedEvent) {
			return terminal("PROCESS_FAILED", false);
		}
		if (event instanceof AgentProcessWaitingEvent) {
			return event("PROCESS_WAITING");
		}
		if (event instanceof AgentProcessPausedEvent) {
			return event("PROCESS_PAUSED");
		}
		if (event instanceof AgentProcessStuckEvent) {
			return terminal("PROCESS_STUCK", false);
		}
		return null;
	}

	private EventMetadata event(String type) {
		return new EventMetadata(type, null, null, null, null, null, null);
	}

	private EventMetadata terminal(String type, boolean successful) {
		return new EventMetadata(type, null, null, null, null, null, successful);
	}

	private EventMetadata action(String type, String actionName, Duration duration, Boolean successful) {
		return new EventMetadata(type, actionName, null, null, null, duration, successful);
	}

	private EventMetadata llm(
		String type,
		String modelName,
		String providerName,
		Duration duration,
		Boolean successful
	) {
		return new EventMetadata(type, null, null, modelName, providerName, duration, successful);
	}

	private String actionName(com.embabel.agent.core.Action action) {
		return action == null ? null : action.getName();
	}

	private Long toMillis(Duration duration) {
		return duration == null ? null : duration.toMillis();
	}

	private String bounded(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= 255 ? value : value.substring(0, 255);
	}

	private record EventMetadata(
		String type,
		String actionName,
		String toolName,
		String modelName,
		String providerName,
		Duration duration,
		Boolean successful
	) {
	}
}
