package com.ypkim.pinbabel.influenceranalysis.application.domain.service;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InfluencerAnalysisReport;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.InstrumentSummary;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.EvaluationCaseResult;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.GoldenEvaluationCase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.jmolecules.ddd.annotation.Service;

@Service
public class GoldenDatasetEvaluator {

	public EvaluationCaseResult evaluate(
		GoldenEvaluationCase goldenCase,
		String analysisRunId,
		String analysisStatus,
		InfluencerAnalysisReport report
	) {
		var expectedById = new HashMap<String, com.ypkim.pinbabel.influenceranalysis.application.domain.model.evaluation.ExpectedInstrument>();
		goldenCase.expectedInstruments().forEach(expected -> expectedById.put(expected.instrumentId(), expected));
		var actualById = new HashMap<String, InstrumentSummary>();
		if (report != null) {
			report.instrumentSummaries().forEach(actual -> actualById.put(actual.instrumentId(), actual));
		}

		var truePositives = 0;
		var correctSentiments = 0;
		var expectedEvidence = 0;
		var matchedEvidence = 0;
		var mismatches = new ArrayList<String>();

		for (var expected : goldenCase.expectedInstruments()) {
			expectedEvidence += expected.evidencePostIds().size();
			var actual = actualById.get(expected.instrumentId());
			if (actual == null) {
				mismatches.add("MISSING_INSTRUMENT:" + expected.instrumentId());
				continue;
			}
			truePositives++;
			if (actual.overallSentiment() == expected.sentiment()) {
				correctSentiments++;
			}
			else {
				mismatches.add("SENTIMENT:" + expected.instrumentId());
			}
			var actualEvidence = new HashSet<>(actual.evidencePostIds());
			for (var postId : expected.evidencePostIds()) {
				if (actualEvidence.contains(postId)) {
					matchedEvidence++;
				}
				else {
					mismatches.add("EVIDENCE:" + expected.instrumentId() + ":" + postId);
				}
			}
		}
		for (var actual : actualById.keySet()) {
			if (!expectedById.containsKey(actual)) {
				mismatches.add("UNEXPECTED_INSTRUMENT:" + actual);
			}
		}

		var falseNegatives = expectedById.size() - truePositives;
		var falsePositives = actualById.size() - truePositives;
		var completed = "COMPLETED".equals(analysisStatus) && report != null;
		return new EvaluationCaseResult(
			goldenCase.caseId(), analysisRunId, analysisStatus,
			expectedById.size(), actualById.size(), truePositives, falsePositives, falseNegatives,
			correctSentiments, expectedEvidence, matchedEvidence,
			completed && mismatches.isEmpty(), mismatches
		);
	}
}
