package com.ypkim.pinbabel.influenceranalysis.application.port.out;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentReference;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface InstrumentCatalog {

	List<InstrumentReference> search(String query, Set<String> marketCodes, int limit);

	Optional<InstrumentReference> findById(String instrumentId);
}
