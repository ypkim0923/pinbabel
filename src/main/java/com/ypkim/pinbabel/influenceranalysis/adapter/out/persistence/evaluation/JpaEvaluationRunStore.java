package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRun;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.EvaluationRunStore;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@SecondaryAdapter
class JpaEvaluationRunStore implements EvaluationRunStore {

	private final EvaluationRunJpaRepository repository;
	private final EvaluationCaseResultsJsonCodec codec;

	JpaEvaluationRunStore(EvaluationRunJpaRepository repository, EvaluationCaseResultsJsonCodec codec) {
		this.repository = repository;
		this.codec = codec;
	}

	@Override
	@Transactional
	public void save(EvaluationRun run) {
		try {
			repository.saveAndFlush(EvaluationRunJpaEntity.from(run, codec.serialize(run.caseResults())));
		}
		catch (InfluencerAnalysisException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.EVALUATION_RUN_SAVE_FAILED,
				"Evaluation run persistence failed",
				exception
			);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<EvaluationRun> findLatest(int limit) {
		try {
			return repository.findAllByOrderByCreatedAtDescEvaluationRunIdDesc(PageRequest.of(0, limit)).stream()
				.map(this::toDomain)
				.toList();
		}
		catch (RuntimeException exception) {
			throw queryFailure(exception);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EvaluationRun> findById(EvaluationRunId runId) {
		try {
			return repository.findById(runId.value()).map(this::toDomain);
		}
		catch (RuntimeException exception) {
			throw queryFailure(exception);
		}
	}

	private EvaluationRun toDomain(EvaluationRunJpaEntity entity) {
		return entity.toDomain(codec.deserialize(entity.resultSchemaVersion(), entity.caseResultsJson()));
	}

	private InfluencerAnalysisException queryFailure(RuntimeException exception) {
		if (exception instanceof InfluencerAnalysisException influencerAnalysisException) {
			return influencerAnalysisException;
		}
		return new InfluencerAnalysisException(
			InfluencerAnalysisInternalCode.EVALUATION_RUN_QUERY_FAILED,
			"Evaluation run query failed",
			exception
		);
	}
}
