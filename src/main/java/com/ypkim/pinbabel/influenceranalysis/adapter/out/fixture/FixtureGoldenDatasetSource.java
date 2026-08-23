package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisException;
import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.Sentiment;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.ExpectedInstrument;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenDataset;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenEvaluationCase;
import com.ypkim.pinbabel.influenceranalysis.application.domain.service.AnalysisScopePolicy;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.evaluation.GoldenDatasetSource;
import java.io.IOException;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("fixture")
@SecondaryAdapter
public class FixtureGoldenDatasetSource implements GoldenDatasetSource {

	static final int MAX_CASES = 20;
	static final int MAX_EXPECTED_INSTRUMENTS = 50;
	static final int MAX_EVIDENCE_IDS = 20;
	private static final String DEFAULT_FIXTURE = "fixtures/influenceranalysis/golden-dataset-v1.json";

	private final ObjectMapper objectMapper;
	private final Resource fixture;

	@Autowired
	public FixtureGoldenDatasetSource(ObjectMapper objectMapper) {
		this(objectMapper, new ClassPathResource(DEFAULT_FIXTURE));
	}

	FixtureGoldenDatasetSource(ObjectMapper objectMapper, Resource fixture) {
		this.objectMapper = objectMapper;
		this.fixture = fixture;
	}

	@Override
	public GoldenDataset load() {
		try (var input = fixture.getInputStream()) {
			var document = objectMapper.readValue(input, GoldenDatasetDocument.class);
			validateBounds(document);
			return new GoldenDataset(
				document.datasetId(),
				document.version(),
				document.cases().stream().map(this::toDomain).toList()
			);
		}
		catch (IOException | JacksonException | IllegalArgumentException exception) {
			throw new InfluencerAnalysisException(
				InfluencerAnalysisInternalCode.GOLDEN_DATASET_READ_FAILED,
				"Unable to read the golden dataset",
				exception
			);
		}
	}

	private void validateBounds(GoldenDatasetDocument document) {
		if (document == null || document.cases() == null || document.cases().size() > MAX_CASES) {
			throw new IllegalArgumentException("Golden dataset case limit exceeded");
		}
		for (var evaluationCase : document.cases()) {
			if (evaluationCase == null
				|| evaluationCase.instruction() == null
				|| evaluationCase.instruction().length() > AnalysisScopePolicy.MAX_INPUT_LENGTH
				|| evaluationCase.expectedInstruments() == null
				|| evaluationCase.expectedInstruments().size() > MAX_EXPECTED_INSTRUMENTS) {
				throw new IllegalArgumentException("Golden dataset case is outside allowed bounds");
			}
			for (var expected : evaluationCase.expectedInstruments()) {
				if (expected == null || expected.evidencePostIds() == null
					|| expected.evidencePostIds().size() > MAX_EVIDENCE_IDS) {
					throw new IllegalArgumentException("Golden dataset expectation is outside allowed bounds");
				}
			}
		}
	}

	private GoldenEvaluationCase toDomain(GoldenCaseDocument document) {
		return new GoldenEvaluationCase(
			document.caseId(),
			document.instruction(),
			document.expectedInstruments().stream()
				.map(expected -> new ExpectedInstrument(
					expected.instrumentId(), Sentiment.valueOf(expected.sentiment()), expected.evidencePostIds()
				))
				.toList()
		);
	}

	private record GoldenDatasetDocument(String datasetId, int version, List<GoldenCaseDocument> cases) {
	}

	private record GoldenCaseDocument(
		String caseId,
		String instruction,
		List<ExpectedInstrumentDocument> expectedInstruments
	) {
	}

	private record ExpectedInstrumentDocument(
		String instrumentId,
		String sentiment,
		List<String> evidencePostIds
	) {
	}
}
