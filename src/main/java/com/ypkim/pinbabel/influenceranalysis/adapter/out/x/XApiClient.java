package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import java.io.IOException;
import java.net.URI;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@FunctionalInterface
@SecondaryAdapter
interface XApiClient {

	XApiResponse get(URI uri) throws IOException, InterruptedException;
}
