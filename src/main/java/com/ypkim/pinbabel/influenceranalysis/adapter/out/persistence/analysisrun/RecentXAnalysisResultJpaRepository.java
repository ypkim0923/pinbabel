package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import org.springframework.data.jpa.repository.JpaRepository;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
interface RecentXAnalysisResultJpaRepository extends JpaRepository<RecentXAnalysisResultJpaEntity, String> {
}
