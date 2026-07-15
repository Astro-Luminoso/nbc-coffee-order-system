package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.dto;

import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.InvalidRequestException;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.ServiceException;
import java.util.List;
import java.util.Locale;

/**
 * API 오류 응답 데이터를 표현한다.
 *
 * @param code 오류 상황을 나타내는 코드
 * @param message 클라이언트에게 반환할 안전한 메시지
 * @param errors 유효하지 않은 필드 정보 목록
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors
) {

    /**
     * 서비스 예외를 필드 오류 없는 API 오류 응답으로 변환한다.
     *
     * @param exception 변환할 서비스 예외
     * @return 생성된 API 오류 응답
     */
    public static ErrorResponse from(ServiceException exception) {
        return of(exception, errorsOf(exception));
    }

    /**
     * 서비스 예외와 필드 오류를 API 오류 응답으로 변환한다.
     *
     * @param exception 변환할 서비스 예외
     * @param errors 유효하지 않은 필드 정보 목록
     * @return 생성된 API 오류 응답
     */
    public static ErrorResponse of(ServiceException exception, List<FieldError> errors) {
        return new ErrorResponse(
                codeOf(exception),
                exception.getMessage(),
                List.copyOf(errors)
        );
    }

    /**
     * 예외 클래스명에서 API 오류 코드를 생성한다.
     *
     * @param exception 오류 코드를 생성할 서비스 예외
     * @return 대문자 스네이크 케이스 오류 코드
     */
    private static String codeOf(ServiceException exception) {
        String simpleName = exception.getClass().getSimpleName();
        String exceptionName = simpleName.endsWith("Exception")
                ? simpleName.substring(0, simpleName.length() - "Exception".length())
                : simpleName;

        return exceptionName
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
    }

    /**
     * 잘못된 요청 예외에 포함된 필드 오류를 반환한다.
     *
     * @param exception 필드 오류를 확인할 서비스 예외
     * @return 필드 오류 목록
     */
    private static List<FieldError> errorsOf(ServiceException exception) {
        if (exception instanceof InvalidRequestException invalidRequestException) {
            return invalidRequestException.errors();
        }
        return List.of();
    }

    /**
     * 유효하지 않은 요청 필드 정보를 표현한다.
     *
     * @param field 유효하지 않은 필드명
     * @param reason 필드가 유효하지 않은 이유
     */
    public record FieldError(
            String field,
            String reason
    ) {

        /**
         * 필드명과 오류 이유로 필드 오류를 생성한다.
         *
         * @param field 유효하지 않은 필드명
         * @param reason 필드가 유효하지 않은 이유
         * @return 생성된 필드 오류
         */
        public static FieldError of(String field, String reason) {
            return new FieldError(field, reason);
        }
    }
}
