package com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenDataset;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface GoldenDatasetSource {

	GoldenDataset load();
}
