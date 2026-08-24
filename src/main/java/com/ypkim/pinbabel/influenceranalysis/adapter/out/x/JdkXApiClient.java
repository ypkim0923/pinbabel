package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

@SecondaryAdapter
final class JdkXApiClient implements XApiClient {

	static final int MAX_RESPONSE_BYTES = 1_048_576;

	private final HttpClient httpClient;
	private final String bearerToken;
	private final Duration requestTimeout;

	JdkXApiClient(String bearerToken, Duration connectTimeout, Duration requestTimeout) {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		this.bearerToken = bearerToken;
		this.requestTimeout = requestTimeout;
	}

	@Override
	public XApiResponse get(URI uri) throws IOException, InterruptedException {
		var startedAt = System.nanoTime();
		var request = HttpRequest.newBuilder(uri)
			.timeout(requestTimeout)
			.header("Authorization", "Bearer " + bearerToken)
			.header("Accept", "application/json")
			.GET()
			.build();
		var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		var remainingNanos = requestTimeout.toNanos() - (System.nanoTime() - startedAt);
		if (remainingNanos <= 0) {
			response.body().close();
			throw new HttpTimeoutException("X API operation timed out before reading the response body");
		}
		var readTask = new FutureTask<>(() -> {
			try (var body = response.body()) {
				return body.readNBytes(MAX_RESPONSE_BYTES + 1);
			}
		});
		Thread.ofVirtual().name("x-api-response-reader").start(readTask);
		byte[] bytes;
		try {
			bytes = readTask.get(remainingNanos, TimeUnit.NANOSECONDS);
		} catch (TimeoutException exception) {
			readTask.cancel(true);
			response.body().close();
			throw new HttpTimeoutException("X API operation timed out while reading the response body");
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			throw new IOException("Unable to read the X API response body", exception.getCause());
		} catch (InterruptedException exception) {
			readTask.cancel(true);
			response.body().close();
			throw exception;
		}
		if (bytes.length > MAX_RESPONSE_BYTES) {
			throw new IOException("X API response exceeded the configured byte limit");
		}
		return new XApiResponse(
			response.statusCode(),
			response.headers().firstValue("Content-Type").orElse(""),
			bytes
		);
	}
}
