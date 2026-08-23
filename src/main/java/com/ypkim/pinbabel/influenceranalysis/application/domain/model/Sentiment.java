package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum Sentiment {
	POSITIVE,
	NEGATIVE,
	NEUTRAL,
	UNCERTAIN
}
