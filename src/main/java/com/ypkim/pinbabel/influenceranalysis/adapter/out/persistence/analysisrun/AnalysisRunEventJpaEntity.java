package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@Entity
@Table(name = "analysis_run_event")
@SecondaryAdapter
class AnalysisRunEventJpaEntity {

	@EmbeddedId
	private AnalysisRunEventJpaId id;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "process_id")
	private String processId;

	@Column(name = "action_name")
	private String actionName;

	@Column(name = "tool_name")
	private String toolName;

	@Column(name = "model_name")
	private String modelName;

	@Column(name = "provider_name")
	private String providerName;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "successful")
	private Boolean successful;

	protected AnalysisRunEventJpaEntity() {
	}

	static AnalysisRunEventJpaEntity from(AnalysisRunId runId, AnalysisTraceEvent event) {
		var entity = new AnalysisRunEventJpaEntity();
		entity.id = new AnalysisRunEventJpaId(runId.value(), event.sequence());
		entity.eventType = event.eventType();
		entity.occurredAt = event.occurredAt();
		entity.processId = event.processId();
		entity.actionName = event.actionName();
		entity.toolName = event.toolName();
		entity.modelName = event.modelName();
		entity.providerName = event.providerName();
		entity.durationMs = event.durationMs();
		entity.successful = event.successful();
		return entity;
	}

	AnalysisTraceEvent toDomain() {
		return new AnalysisTraceEvent(
			id.sequence(), eventType, occurredAt, processId, actionName, toolName,
			modelName, providerName, durationMs, successful
		);
	}
}
