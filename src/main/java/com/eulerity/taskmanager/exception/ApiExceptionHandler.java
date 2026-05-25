package com.eulerity.taskmanager.exception;

import java.util.Comparator;
import java.util.List;

import com.eulerity.taskmanager.ai.AiTaskException;
import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;
import com.eulerity.taskmanager.ai.MissingOpenAiConfigurationException;
import com.eulerity.taskmanager.dto.ApiErrorResponse;
import com.eulerity.taskmanager.dto.FieldValidationError;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(TaskNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleTaskNotFound(TaskNotFoundException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "TASK_NOT_FOUND", ex.getMessage(),
					request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<FieldValidationError> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
			.sorted(Comparator.comparing(FieldValidationError::field))
			.toList();

		return ResponseEntity.badRequest()
			.body(ApiErrorResponse.validation(HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
					"Request validation failed", request.getRequestURI(), fieldErrors));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
		return ResponseEntity.badRequest()
			.body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST",
					"Request body or parameters are invalid", request.getRequestURI()));
	}

	@ExceptionHandler(InvalidTaskListSortException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidTaskListSort(InvalidTaskListSortException ex,
			HttpServletRequest request) {
		return ResponseEntity.badRequest()
			.body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", ex.getMessage(),
					request.getRequestURI()));
	}

	@ExceptionHandler(MissingOpenAiConfigurationException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingOpenAiConfiguration(MissingOpenAiConfigurationException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(ApiErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(), "AI_CONFIGURATION_MISSING",
					ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(AiTaskInvalidOutputException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidAiTaskOutput(AiTaskInvalidOutputException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
			.body(ApiErrorResponse.of(HttpStatus.BAD_GATEWAY.value(), "AI_TASK_OUTPUT_INVALID", ex.getMessage(),
					request.getRequestURI()));
	}

	@ExceptionHandler(AiTaskException.class)
	public ResponseEntity<ApiErrorResponse> handleAiTaskFailure(AiTaskException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
			.body(ApiErrorResponse.of(HttpStatus.BAD_GATEWAY.value(), "AI_TASK_FAILED", ex.getMessage(),
					request.getRequestURI()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Resource was not found",
					request.getRequestURI()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
					"An unexpected error occurred", request.getRequestURI()));
	}
}
