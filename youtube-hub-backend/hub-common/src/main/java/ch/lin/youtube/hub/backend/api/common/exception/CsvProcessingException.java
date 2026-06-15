package ch.lin.youtube.hub.backend.api.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an error occurs during CSV import or export processing.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class CsvProcessingException extends RuntimeException {

    public CsvProcessingException(String message) {
        super(message);
    }

    public CsvProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
