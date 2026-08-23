package com.ypkim.pinbabel.influenceranalysis.adapter.out.persistence.analysisrun;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@Embeddable
@SecondaryAdapter
class AnalysisRunEventJpaId implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Column(name = "run_id", nullable = false, length = 36)
	private String runId;

	@Column(name = "event_sequence", nullable = false)
	private long sequence;

	protected AnalysisRunEventJpaId() {
	}

	AnalysisRunEventJpaId(String runId, long sequence) {
		this.runId = runId;
		this.sequence = sequence;
	}

	String runId() { return runId; }
	long sequence() { return sequence; }

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AnalysisRunEventJpaId that)) {
			return false;
		}
		return sequence == that.sequence && Objects.equals(runId, that.runId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(runId, sequence);
	}
}
