package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
interface AnalysisRunEventJpaRepository
	extends JpaRepository<AnalysisRunEventJpaEntity, AnalysisRunEventJpaId> {

	List<AnalysisRunEventJpaEntity> findByIdRunIdOrderByIdSequenceAsc(String runId);
}
