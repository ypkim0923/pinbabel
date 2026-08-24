package com.ypkim.pinbabel.influenceranalysis.adapter.out.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.analysisrun.AnalysisRunId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BoundedAnalysisExecutionLauncherTest {

	@Test
	void rejectsNewExecutionWhenWorkersAndQueueAreFull() throws InterruptedException {
		var launcher = new BoundedAnalysisExecutionLauncher(1, 1);
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try {
			assertThat(launcher.launch(AnalysisRunId.newId(), () -> {
				started.countDown();
				await(release);
			})).isTrue();
			assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
			assertThat(launcher.launch(AnalysisRunId.newId(), () -> { })).isTrue();

			assertThat(launcher.launch(AnalysisRunId.newId(), () -> { })).isFalse();
		}
		finally {
			release.countDown();
			launcher.shutdown();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
