package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunJpaEntity, String> {

	List<AnalysisRunJpaEntity> findAllByOrderByCreatedAtDescRunIdDesc(Pageable pageable);
}
