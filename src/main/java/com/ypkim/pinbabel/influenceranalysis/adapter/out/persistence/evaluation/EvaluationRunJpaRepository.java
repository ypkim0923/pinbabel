package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.evaluation;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@SecondaryAdapter
interface EvaluationRunJpaRepository extends JpaRepository<EvaluationRunJpaEntity, String> {

	List<EvaluationRunJpaEntity> findAllByOrderByCreatedAtDescEvaluationRunIdDesc(Pageable pageable);
}
