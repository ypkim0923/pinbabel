package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
record XApiResponse(int statusCode, String contentType, byte[] body) {

	XApiResponse {
		body = body.clone();
	}

	@Override
	public byte[] body() {
		return body.clone();
	}
}
