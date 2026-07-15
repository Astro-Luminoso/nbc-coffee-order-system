package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.dto.ErrorResponse.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 요청 형식 또는 요청 값이 유효하지 않을 때 발생하는 예외이다.
 */
public class InvalidRequestException extends ServiceException {

    private final List<FieldError> errors;

    /**
     * 필드 오류 없이 잘못된 요청 예외를 생성한다.
     *
     * @param message 클라이언트에게 반환할 안전한 메시지
     */
    public InvalidRequestException(String message) {
        this(message, List.of());
    }

    /**
     * 필드 오류를 포함한 잘못된 요청 예외를 생성한다.
     *
     * @param message 클라이언트에게 반환할 안전한 메시지
     * @param errors 유효하지 않은 요청 필드 목록
     */
    public InvalidRequestException(String message, List<FieldError> errors) {
        super(message, HttpStatus.BAD_REQUEST);
        this.errors = List.copyOf(errors);
    }

    /**
     * 유효하지 않은 요청 필드 목록을 반환한다.
     *
     * @return 유효하지 않은 요청 필드 목록
     */
    public List<FieldError> errors() {
        return errors;
    }
}
