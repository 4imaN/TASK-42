package com.reclaim.portal.unit;

import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GlobalExceptionHandler centralized exception handling.
 * Verifies response status codes, safe message handling, and that
 * the generic handler does not leak internal exception details.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnConflictForBusinessRuleException() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBusinessRule(new BusinessRuleException("Order already completed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
        assertThat(response.getBody()).containsEntry("message", "Order already completed");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void shouldReturnNotFoundForEntityNotFoundException() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleEntityNotFound(new EntityNotFoundException("Contract", 42L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    @Test
    void shouldReturnForbiddenForAccessDeniedException() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("Insufficient privileges"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403);
        assertThat(response.getBody()).containsEntry("error", "Forbidden");
    }

    @Test
    void genericHandlerShouldNotLeakExceptionMessageToClient() {
        RuntimeException internalError = new RuntimeException(
                "java.sql.SQLException: connection pool exhausted at line 42");

        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(internalError);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        // The response message should be generic, NOT the internal exception message
        assertThat(response.getBody().get("message").toString())
                .doesNotContain("SQLException")
                .doesNotContain("connection pool")
                .isEqualTo("An unexpected error occurred");
    }

    @Test
    void allResponsesShouldContainTimestamp() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("test"));
        assertThat(response.getBody()).containsKey("timestamp");
        assertThat(response.getBody().get("timestamp").toString()).isNotBlank();
    }
}
