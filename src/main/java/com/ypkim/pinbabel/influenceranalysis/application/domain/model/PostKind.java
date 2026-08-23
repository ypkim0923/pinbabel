package com.ypkim.pinbabel.influenceranalysis.application.domain.model;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum PostKind {
	ORIGINAL,
	REPLY,
	QUOTE,
	REPOST
}
