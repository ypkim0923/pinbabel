package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@Entity
@Table(name = "recent_x_analysis_result")
@SecondaryAdapter
class RecentXAnalysisResultJpaEntity {

	@Id
	@Column(name = "run_id", nullable = false, length = 36)
	private String runId;

	@Column(name = "schema_version", nullable = false)
	private int schemaVersion;

	@Lob
	@Column(name = "result_json", nullable = false)
	private String resultJson;

	protected RecentXAnalysisResultJpaEntity() {
	}

	RecentXAnalysisResultJpaEntity(String runId, int schemaVersion, String resultJson) {
		this.runId = runId;
		this.schemaVersion = schemaVersion;
		this.resultJson = resultJson;
	}

	int schemaVersion() { return schemaVersion; }
	String resultJson() { return resultJson; }
}
