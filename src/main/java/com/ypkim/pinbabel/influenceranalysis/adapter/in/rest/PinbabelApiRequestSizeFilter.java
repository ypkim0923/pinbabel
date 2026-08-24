package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("fixture & api")
@PrimaryAdapter
public class PinbabelApiRequestSizeFilter extends OncePerRequestFilter {
	static final int MAX_BODY_BYTES = 16 * 1024;

	private final ObjectMapper objectMapper;

	public PinbabelApiRequestSizeFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		var path = request.getRequestURI();
		var method = request.getMethod();
		return !("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
			|| !(path.startsWith("/api/") || path.startsWith("/a2a") || path.startsWith("/a2ui/"));
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		var body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
		if (body.length > MAX_BODY_BYTES) {
			writeTooLarge(response, request.getRequestURI());
			return;
		}
		filterChain.doFilter(new CachedBodyRequest(request, body), response);
	}

	private void writeTooLarge(HttpServletResponse response, String path) throws IOException {
		var exception = PinbabelApiException.bodyTooLarge();
		response.setStatus(exception.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), new PinbabelApiErrorResponse(
			Instant.now(), exception.status().value(), exception.publicCode(),
			exception.internalCode().value(), exception.getMessage(), path
		));
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {
		private final byte[] body;

		private CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			var input = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override public boolean isFinished() { return input.available() == 0; }
				@Override public boolean isReady() { return true; }
				@Override public void setReadListener(ReadListener readListener) { }
				@Override public int read() { return input.read(); }
			};
		}
	}
}
