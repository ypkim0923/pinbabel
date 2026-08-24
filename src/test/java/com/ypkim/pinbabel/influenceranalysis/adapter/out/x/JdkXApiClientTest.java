package com.ypkim.pinbabel.influenceranalysis.adapter.out.x;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JdkXApiClientTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsRequiredHeadersAndCapturesResponseMetadata() throws Exception {
		var authorization = new AtomicReference<String>();
		var accept = new AtomicReference<String>();
		startServer(exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			accept.set(exchange.getRequestHeaders().getFirst("Accept"));
			respond(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
		});
		var client = client(Duration.ofSeconds(2));

		var response = client.get(uri("/posts"));

		assertThat(authorization).hasValue("Bearer secret-token");
		assertThat(accept).hasValue("application/json");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.contentType()).isEqualTo("application/json; charset=utf-8");
		assertThat(response.body()).asString(StandardCharsets.UTF_8).isEqualTo("{\"ok\":true}");
	}

	@Test
	void doesNotFollowRedirects() throws Exception {
		var redirectedCalls = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().set("Location", uri("/target").toString());
			respond(exchange, 302, "application/json", new byte[0]);
		});
		server.createContext("/target", exchange -> {
			redirectedCalls.incrementAndGet();
			respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
		});
		server.start();

		var response = client(Duration.ofSeconds(2)).get(uri("/redirect"));

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(redirectedCalls).hasValue(0);
	}

	@Test
	void enforcesTheExactResponseByteLimit() throws Exception {
		var allowed = new byte[JdkXApiClient.MAX_RESPONSE_BYTES];
		startServer(exchange -> respond(exchange, 200, "application/json", allowed));

		var response = client(Duration.ofSeconds(2)).get(uri("/allowed"));

		assertThat(response.body()).hasSize(JdkXApiClient.MAX_RESPONSE_BYTES);
		stopServer();
		server = null;
		var rejected = new byte[JdkXApiClient.MAX_RESPONSE_BYTES + 1];
		startServer(exchange -> respond(exchange, 200, "application/json", rejected));

		assertThatThrownBy(() -> client(Duration.ofSeconds(2)).get(uri("/rejected")))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("byte limit");
	}

	@Test
	void boundsSlowResponseBodyReadsByTheOperationTimeout() throws Exception {
		startServer(exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, 0);
			exchange.getResponseBody().flush();
			try {
				Thread.sleep(Duration.ofSeconds(1));
				exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});

		assertThatThrownBy(() -> client(Duration.ofMillis(150)).get(uri("/slow")))
			.isInstanceOf(HttpTimeoutException.class);
	}

	private JdkXApiClient client(Duration operationTimeout) {
		return new JdkXApiClient("secret-token", Duration.ofSeconds(1), operationTimeout);
	}

	private void startServer(HttpHandler handler) throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", handler);
		server.start();
	}

	private URI uri(String path) {
		return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path);
	}

	private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(status, body.length);
		try (exchange; var responseBody = exchange.getResponseBody()) {
			responseBody.write(body);
		}
	}
}
