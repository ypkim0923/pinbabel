package com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunMetrics;

public interface AnalysisRunFlightRecorder extends AutoCloseable {

	String STORAGE_WARNING = "TRACE_STORAGE_UNAVAILABLE";
	String TRUNCATED_WARNING = "TRACE_EVENT_LIMIT_REACHED";
	String LISTENER_WARNING = "TRACE_LISTENER_FAILED";

	boolean traceAvailable();

	String warningCode();

	String processId();

	AnalysisRunMetrics metrics();

	@Override
	void close();
}
