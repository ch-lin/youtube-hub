package ch.lin.youtube.hub.backend.api.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class CsvProcessingExceptionTest {

    @Test
    void shouldConstructWithMessage() {
        String message = "Test message";
        CsvProcessingException exception = new CsvProcessingException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void shouldConstructWithMessageAndCause() {
        String message = "Test message";
        Throwable cause = new RuntimeException("Cause");
        CsvProcessingException exception = new CsvProcessingException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldHaveResponseStatusAnnotation() {
        ResponseStatus responseStatus = CsvProcessingException.class.getAnnotation(ResponseStatus.class);

        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
