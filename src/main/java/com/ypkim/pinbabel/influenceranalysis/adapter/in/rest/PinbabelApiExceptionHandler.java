package com.ypkim.pinbabel.influenceranalysis.adapter.in.rest;

import com.ypkim.pinbabel.influenceranalysis.application.domain.error.InfluencerAnalysisInternalCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Profile("fixture & api")
@PrimaryAdapter
public class PinbabelApiExceptionHandler {

	@ExceptionHandler(PinbabelApiException.class)
	ResponseEntity<PinbabelApiErrorResponse> handleKnown(PinbabelApiException exception, HttpServletRequest request) {
		return response(exception.status(), exception.publicCode(), exception.internalCode(), exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<PinbabelApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		return handleKnown(PinbabelApiException.invalidRequest(), request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<PinbabelApiErrorResponse> handleMalformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
		return handleKnown(PinbabelApiException.invalidRequest(), request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<PinbabelApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		return handleKnown(PinbabelApiException.unexpected(), request);
	}

	private ResponseEntity<PinbabelApiErrorResponse> response(
		HttpStatus status,
		String code,
		InfluencerAnalysisInternalCode internalCode,
		String message,
		HttpServletRequest request
	) {
		return ResponseEntity.status(status).body(new PinbabelApiErrorResponse(
			Instant.now(), status.value(), code, internalCode.value(), message, request.getRequestURI()
		));
	}
}
