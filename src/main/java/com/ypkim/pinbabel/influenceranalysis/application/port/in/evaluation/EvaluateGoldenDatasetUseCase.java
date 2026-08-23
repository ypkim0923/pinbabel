package com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation;

import com.ypkim.pinbabel.influenceranalysis.application.port.in.evaluation.dto.EvaluationRunDetailResource;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface EvaluateGoldenDatasetUseCase {

	EvaluationRunDetailResource evaluate();
}
