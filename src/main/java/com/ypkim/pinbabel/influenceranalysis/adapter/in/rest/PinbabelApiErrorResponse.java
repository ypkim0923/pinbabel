package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import java.time.Instant;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

@PrimaryAdapter
public record PinbabelApiErrorResponse(
	Instant timestamp,
	int status,
	String code,
	String internalCode,
	String message,
	String path
) {
}
