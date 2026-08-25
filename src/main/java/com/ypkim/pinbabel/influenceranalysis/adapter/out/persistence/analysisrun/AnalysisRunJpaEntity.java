package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@Entity
@Table(name = "analysis_run")
@SecondaryAdapter
class AnalysisRunJpaEntity {

	@Id
	@Column(name = "run_id", nullable = false, length = 36)
	private String runId;

	@Column(name = "correlation_id", nullable = false, unique = true, length = 36)
	private String correlationId;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "embabel_process_id")
	private String embabelProcessId;

	@Column(name = "trace_available", nullable = false)
	private boolean traceAvailable;

	@Column(name = "warning_code", length = 100)
	private String warningCode;

	@Column(name = "outcome_code", length = 100)
	private String outcomeCode;

	@Column(name = "outcome_summary", length = 500)
	private String outcomeSummary;

	@Column(name = "prompt_tokens")
	private Long promptTokens;

	@Column(name = "completion_tokens")
	private Long completionTokens;

	@Column(name = "cost_usd", precision = 19, scale = 8)
	private BigDecimal costUsd;

	@Lob
	@Column(name = "models_json", length = Integer.MAX_VALUE)
	private String modelsJson;

	@Column(name = "report_schema_version")
	private Integer reportSchemaVersion;

	@Lob
	@Column(name = "report_json", length = Integer.MAX_VALUE)
	private String reportJson;

	protected AnalysisRunJpaEntity() {
	}

	static AnalysisRunJpaEntity from(AnalysisRun run) {
		var entity = new AnalysisRunJpaEntity();
		entity.runId = run.id().value();
		entity.correlationId = run.correlationId().value();
		entity.createdAt = run.createdAt();
		return entity;
	}

	void update(AnalysisRun run, String modelsJson, Integer reportSchemaVersion, String reportJson) {
		this.status = run.status().name();
		this.startedAt = run.startedAt();
		this.completedAt = run.completedAt();
		this.durationMs = run.durationMs();
		this.embabelProcessId = run.embabelProcessId();
		this.traceAvailable = run.traceAvailable();
		this.warningCode = run.warningCode();
		this.outcomeCode = run.outcomeCode();
		this.outcomeSummary = run.outcomeSummary();
		this.promptTokens = run.metrics().promptTokens();
		this.completionTokens = run.metrics().completionTokens();
		this.costUsd = run.metrics().costUsd();
		this.modelsJson = modelsJson;
		if (reportJson != null) {
			this.reportSchemaVersion = reportSchemaVersion;
			this.reportJson = reportJson;
		}
	}

	String runId() { return runId; }
	String correlationId() { return correlationId; }
	String status() { return status; }
	Instant createdAt() { return createdAt; }
	Instant startedAt() { return startedAt; }
	Instant completedAt() { return completedAt; }
	Long durationMs() { return durationMs; }
	boolean traceAvailable() { return traceAvailable; }
	String warningCode() { return warningCode; }
	String outcomeCode() { return outcomeCode; }
	String outcomeSummary() { return outcomeSummary; }
	Long promptTokens() { return promptTokens; }
	Long completionTokens() { return completionTokens; }
	BigDecimal costUsd() { return costUsd; }
	String modelsJson() { return modelsJson; }
	Integer reportSchemaVersion() { return reportSchemaVersion; }
	String reportJson() { return reportJson; }
}
