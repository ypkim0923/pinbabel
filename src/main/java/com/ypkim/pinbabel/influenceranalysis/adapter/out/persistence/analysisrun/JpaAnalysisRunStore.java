package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunStatus;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisTraceEvent;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisRunStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunDetail;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredAnalysisRunSummary;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@SecondaryAdapter
class JpaAnalysisRunStore implements AnalysisRunStore {

	private final AnalysisRunJpaRepository runRepository;
	private final AnalysisRunEventJpaRepository eventRepository;
	private final AnalysisReportJsonCodec reportCodec;

	JpaAnalysisRunStore(
		AnalysisRunJpaRepository runRepository,
		AnalysisRunEventJpaRepository eventRepository,
		AnalysisReportJsonCodec reportCodec
	) {
		this.runRepository = runRepository;
		this.eventRepository = eventRepository;
		this.reportCodec = reportCodec;
	}

	@Override
	@Transactional
	public void save(AnalysisRun run, InfluencerAnalysisReport report) {
		try {
			var reportJson = report == null ? null : reportCodec.serialize(report);
			var entity = runRepository.findById(run.id().value())
				.orElseGet(() -> AnalysisRunJpaEntity.from(run));
			entity.update(
				run,
				String.join("\n", run.metrics().models()),
				report == null ? null : AnalysisReportJsonCodec.SCHEMA_VERSION,
				reportJson
			);
			runRepository.saveAndFlush(entity);
		}
		catch (InfluencerAnalysisException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_RUN_SAVE_FAILED,
				"Analysis run persistence failed",
				exception
			);
		}
	}

	@Override
	@Transactional
	public void append(AnalysisRunId runId, AnalysisTraceEvent event) {
		try {
			eventRepository.saveAndFlush(AnalysisRunEventJpaEntity.from(runId, event));
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.ANALYSIS_TRACE_APPEND_FAILED,
				"Analysis trace persistence failed",
				exception
			);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<StoredAnalysisRunSummary> findLatest(int limit) {
		try {
			return runRepository.findAllByOrderByCreatedAtDescRunIdDesc(PageRequest.of(0, limit)).stream()
				.map(this::toSummary)
				.toList();
		}
		catch (RuntimeException exception) {
			throw queryFailure(exception);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StoredAnalysisRunDetail> findById(AnalysisRunId runId) {
		try {
			return runRepository.findById(runId.value()).map(entity -> toDetail(
				entity,
				eventRepository.findByIdRunIdOrderByIdSequenceAsc(runId.value())
			));
		}
		catch (RuntimeException exception) {
			throw queryFailure(exception);
		}
	}

	private StoredAnalysisRunSummary toSummary(AnalysisRunJpaEntity entity) {
		return new StoredAnalysisRunSummary(
			entity.runId(),
			entity.correlationId(),
			AnalysisRunStatus.valueOf(entity.status()),
			entity.createdAt(),
			entity.startedAt(),
			entity.durationMs(),
			entity.traceAvailable()
		);
	}

	private StoredAnalysisRunDetail toDetail(
		AnalysisRunJpaEntity entity,
		List<AnalysisRunEventJpaEntity> events
	) {
		return new StoredAnalysisRunDetail(
			entity.runId(),
			entity.correlationId(),
			AnalysisRunStatus.valueOf(entity.status()),
			entity.createdAt(),
			entity.startedAt(),
			entity.completedAt(),
			entity.durationMs(),
			entity.traceAvailable(),
			entity.warningCode(),
			entity.outcomeCode(),
			entity.outcomeSummary(),
			new AnalysisRunMetrics(
				entity.promptTokens(), entity.completionTokens(), entity.costUsd(), decodeModels(entity.modelsJson())
			),
			reportCodec.deserialize(entity.reportSchemaVersion(), entity.reportJson()),
			events.stream().map(AnalysisRunEventJpaEntity::toDomain).toList()
		);
	}

	private List<String> decodeModels(String modelsJson) {
		return modelsJson == null || modelsJson.isBlank()
			? List.of()
			: Arrays.stream(modelsJson.split("\n")).toList();
	}

	private InfluencerAnalysisException queryFailure(RuntimeException exception) {
		if (exception instanceof InfluencerAnalysisException influencerAnalysisException) {
			return influencerAnalysisException;
		}
		return new InfluencerAnalysisException(
			InfluencerAnalysisInternalCode.ANALYSIS_RUN_QUERY_FAILED,
			"Analysis run query failed",
			exception
		);
	}
}
