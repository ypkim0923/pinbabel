package com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun;

import java.time.Duration;
import java.time.Instant;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public final class AnalysisRun {

	@Identity
	private final AnalysisRunId id;
	private final AnalysisCorrelationId correlationId;
	private final Instant createdAt;
	private AnalysisRunStatus status;
	private Instant startedAt;
	private Instant completedAt;
	private boolean traceAvailable;
	private String warningCode;
	private String outcomeCode;
	private String outcomeSummary;
	private String embabelProcessId;
	private AnalysisRunMetrics metrics;

	private AnalysisRun(AnalysisRunId id, AnalysisCorrelationId correlationId, Instant createdAt) {
		if (id == null || correlationId == null || createdAt == null) {
			throw new IllegalArgumentException("Analysis run identifiers and creation time are required");
		}
		this.id = id;
		this.correlationId = correlationId;
		this.createdAt = createdAt;
		this.status = AnalysisRunStatus.CREATED;
		this.traceAvailable = true;
		this.metrics = AnalysisRunMetrics.EMPTY;
	}

	public static AnalysisRun create(AnalysisRunId id, AnalysisCorrelationId correlationId, Instant createdAt) {
		return new AnalysisRun(id, correlationId, createdAt);
	}

	public static AnalysisRun create(AnalysisRunId id, Instant createdAt) {
		return create(id, AnalysisCorrelationId.newId(), createdAt);
	}

	public void start(Instant occurredAt) {
		requireStatus(AnalysisRunStatus.CREATED);
		requireNotBeforeCreation(occurredAt);
		this.status = AnalysisRunStatus.RUNNING;
		this.startedAt = occurredAt;
	}

	public void complete(Instant occurredAt, String outcomeCode, String outcomeSummary) {
		terminate(AnalysisRunStatus.RUNNING, AnalysisRunStatus.COMPLETED, occurredAt, outcomeCode, outcomeSummary);
	}

	public void fail(Instant occurredAt, String outcomeCode, String outcomeSummary) {
		terminate(AnalysisRunStatus.RUNNING, AnalysisRunStatus.FAILED, occurredAt, outcomeCode, outcomeSummary);
	}

	public void reject(Instant occurredAt, String outcomeCode, String outcomeSummary) {
		if (status != AnalysisRunStatus.CREATED && status != AnalysisRunStatus.RUNNING) {
			throw new IllegalStateException("Only created or running analysis can be rejected");
		}
		requireNotBeforeCreation(occurredAt);
		if (startedAt != null && occurredAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("Completion time must not be before start time");
		}
		this.status = AnalysisRunStatus.REJECTED;
		this.completedAt = occurredAt;
		this.outcomeCode = requireText(outcomeCode, "Outcome code is required");
		this.outcomeSummary = requireText(outcomeSummary, "Outcome summary is required");
	}

	public void degradeTrace(String warningCode) {
		this.traceAvailable = false;
		this.warningCode = requireText(warningCode, "Trace warning code is required");
	}

	public void attachEmbabelProcess(String processId) {
		this.embabelProcessId = requireText(processId, "Embabel process identifier is required");
	}

	public void recordMetrics(AnalysisRunMetrics metrics) {
		this.metrics = metrics == null ? AnalysisRunMetrics.EMPTY : metrics;
	}

	private void terminate(
		AnalysisRunStatus expected,
		AnalysisRunStatus terminal,
		Instant occurredAt,
		String outcomeCode,
		String outcomeSummary
	) {
		requireStatus(expected);
		if (occurredAt == null || occurredAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("Completion time must not be before start time");
		}
		this.status = terminal;
		this.completedAt = occurredAt;
		this.outcomeCode = requireText(outcomeCode, "Outcome code is required");
		this.outcomeSummary = requireText(outcomeSummary, "Outcome summary is required");
	}

	private void requireStatus(AnalysisRunStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("Expected analysis run status %s but was %s".formatted(expected, status));
		}
	}

	private void requireNotBeforeCreation(Instant occurredAt) {
		if (occurredAt == null || occurredAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("Occurrence time must not be before creation time");
		}
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	public AnalysisRunId id() {
		return id;
	}

	public AnalysisCorrelationId correlationId() {
		return correlationId;
	}

	public AnalysisRunStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant startedAt() {
		return startedAt;
	}

	public Instant completedAt() {
		return completedAt;
	}

	public Long durationMs() {
		if (completedAt == null) {
			return null;
		}
		var baseline = startedAt == null ? createdAt : startedAt;
		return Duration.between(baseline, completedAt).toMillis();
	}

	public boolean traceAvailable() {
		return traceAvailable;
	}

	public String warningCode() {
		return warningCode;
	}

	public String outcomeCode() {
		return outcomeCode;
	}

	public String outcomeSummary() {
		return outcomeSummary;
	}

	public String embabelProcessId() {
		return embabelProcessId;
	}

	public AnalysisRunMetrics metrics() {
		return metrics;
	}
}
