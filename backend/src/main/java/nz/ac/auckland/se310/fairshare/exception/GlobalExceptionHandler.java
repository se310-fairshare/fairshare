package nz.ac.auckland.se310.fairshare.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_KEY = "error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGroupNotFound(GroupNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(ERROR_KEY, "Group not found"));
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleGroupAccessDenied(
            GroupAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(GroupMemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGroupMemberNotFound(
            GroupMemberNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(GroupMemberConflictException.class)
    public ResponseEntity<Map<String, String>> handleGroupMemberConflict(
            GroupMemberConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(InvalidPayerException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPayer(InvalidPayerException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(ERROR_KEY, "Payer must be a member of the group"));
    }
}
