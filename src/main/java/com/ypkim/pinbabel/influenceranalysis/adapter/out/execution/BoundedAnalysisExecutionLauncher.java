package com.ypkim.pinbabel.influenceranalysis.adapter.out.execution;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.analysisrun.AnalysisExecutionLauncher;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("fixture")
@SecondaryAdapter
public class BoundedAnalysisExecutionLauncher implements AnalysisExecutionLauncher {

	static final int WORKER_COUNT = 2;
	static final int QUEUE_CAPACITY = 16;

	private final ThreadPoolExecutor executor;

	public BoundedAnalysisExecutionLauncher() {
		this(WORKER_COUNT, QUEUE_CAPACITY);
	}

	BoundedAnalysisExecutionLauncher(int workerCount, int queueCapacity) {
		this.executor = new ThreadPoolExecutor(
			workerCount,
			workerCount,
			0,
			TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(queueCapacity),
			Thread.ofPlatform().name("pinbabel-analysis-", 0).factory(),
			new ThreadPoolExecutor.AbortPolicy()
		);
	}

	@Override
	public boolean launch(AnalysisRunId runId, Runnable execution) {
		if (runId == null || execution == null) {
			throw new IllegalArgumentException("Analysis run and execution are required");
		}
		try {
			executor.execute(execution);
			return true;
		}
		catch (RejectedExecutionException exception) {
			return false;
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}
}
