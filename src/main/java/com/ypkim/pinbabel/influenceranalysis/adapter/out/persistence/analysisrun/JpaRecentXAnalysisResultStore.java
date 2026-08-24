package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.RecentXAnalysisResultStore;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.dto.StoredRecentXAnalysisResult;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@SecondaryAdapter
class JpaRecentXAnalysisResultStore implements RecentXAnalysisResultStore {

	private final RecentXAnalysisResultJpaRepository repository;
	private final RecentXAnalysisResultJsonCodec codec;

	JpaRecentXAnalysisResultStore(RecentXAnalysisResultJpaRepository repository, RecentXAnalysisResultJsonCodec codec) {
		this.repository = repository;
		this.codec = codec;
	}

	@Override
	@Transactional
	public void save(AnalysisRunId runId, StoredRecentXAnalysisResult result) {
		try {
			repository.saveAndFlush(new RecentXAnalysisResultJpaEntity(
				runId.value(), RecentXAnalysisResultJsonCodec.SCHEMA_VERSION, codec.serialize(result)
			));
		}
		catch (InfluencerAnalysisException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_RESULT_SAVE_FAILED,
				"Recent X analysis result persistence failed", exception
			);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StoredRecentXAnalysisResult> findByRunId(AnalysisRunId runId) {
		try {
			return repository.findById(runId.value())
				.map(entity -> codec.deserialize(entity.schemaVersion(), entity.resultJson()));
		}
		catch (InfluencerAnalysisException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.RECENT_RESULT_QUERY_FAILED,
				"Recent X analysis result query failed", exception
			);
		}
	}
}
