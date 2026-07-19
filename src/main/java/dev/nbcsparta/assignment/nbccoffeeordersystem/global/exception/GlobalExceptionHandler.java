package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.dto.ErrorResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.dto.ErrorResponse.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 애플리케이션 예외를 공통 API 오류 응답으로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_REQUEST_MESSAGE = "요청 값이 올바르지 않습니다.";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "서버 내부 오류가 발생했습니다.";
    private static final String DEFAULT_FIELD_NAME = "request";
    private static final String DEFAULT_FIELD_REASON = "유효하지 않은 값입니다.";

    /**
     * 서비스 예외를 공통 API 오류 응답으로 변환한다.
     *
     * @param exception 처리할 서비스 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleServiceException(ServiceException exception) {
        return errorResponse(exception);
    }

    /**
     * 요청 본문 검증 오류를 잘못된 요청 예외로 변환한다.
     *
     * @param exception 처리할 요청 본문 검증 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        return errorResponse(new InvalidRequestException(
                INVALID_REQUEST_MESSAGE,
                fieldErrors(exception.getAllErrors())
        ));
    }

    /**
     * 모델 바인딩 검증 오류를 잘못된 요청 예외로 변환한다.
     *
     * @param exception 처리할 모델 바인딩 검증 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleBindException(BindException exception) {
        return errorResponse(new InvalidRequestException(
                INVALID_REQUEST_MESSAGE,
                fieldErrors(exception.getAllErrors())
        ));
    }

    /**
     * 컨트롤러 메서드 파라미터 검증 오류를 잘못된 요청 예외로 변환한다.
     *
     * @param exception 처리할 컨트롤러 메서드 검증 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        return errorResponse(new InvalidRequestException(
                INVALID_REQUEST_MESSAGE,
                handlerMethodFieldErrors(exception)
        ));
    }

    /**
     * 제약 조건 검증 오류를 잘못된 요청 예외로 변환한다.
     *
     * @param exception 처리할 제약 조건 검증 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        List<FieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> FieldError.of(
                        fieldName(violation.getPropertyPath().toString()),
                        safeReason(violation.getMessage())
                ))
                .toList();
        return errorResponse(new InvalidRequestException(INVALID_REQUEST_MESSAGE, errors));
    }

    /**
     * 잘못된 JSON 또는 HTTP 요청 바인딩 오류를 잘못된 요청 예외로 변환한다.
     *
     * @param exception 처리할 잘못된 요청 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleMalformedRequestException(Exception exception) {
        return errorResponse(new InvalidRequestException(INVALID_REQUEST_MESSAGE));
    }

    /**
     * 예상하지 못한 예외를 안전한 서버 오류 응답으로 변환한다.
     *
     * @param exception 처리할 예상하지 못한 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<ErrorResponse>> handleUnexpectedException(Exception exception) {
        log.error("예상하지 못한 오류가 발생했습니다.", exception);
        return errorResponse(new InternalServerErrorException(INTERNAL_SERVER_ERROR_MESSAGE));
    }

    /**
     * 서비스 예외를 HTTP 상태와 공통 API 오류 응답으로 변환한다.
     *
     * @param exception 변환할 서비스 예외
     * @return HTTP 상태와 공통 API 오류 응답
     */
    private ResponseEntity<CommonApiResponse<ErrorResponse>> errorResponse(ServiceException exception) {
        ErrorResponse errorResponse = ErrorResponse.from(exception);
        CommonApiResponse<ErrorResponse> response = CommonApiResponse.of(
                exception.httpStatus().value(),
                errorResponse
        );
        return ResponseEntity.status(exception.httpStatus()).body(response);
    }

    /**
     * 스프링 바인딩 오류를 API 필드 오류 목록으로 변환한다.
     *
     * @param errors 변환할 바인딩 오류 목록
     * @return API 필드 오류 목록
     */
    private List<FieldError> fieldErrors(List<ObjectError> errors) {
        return errors.stream()
                .map(error -> FieldError.of(
                        error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField()
                                : DEFAULT_FIELD_NAME,
                        safeReason(error.getDefaultMessage())
                ))
                .toList();
    }

    /**
     * 컨트롤러 메서드 검증 오류를 API 필드 오류 목록으로 변환한다.
     *
     * @param exception 변환할 컨트롤러 메서드 검증 예외
     * @return API 필드 오류 목록
     */
    private List<FieldError> handlerMethodFieldErrors(HandlerMethodValidationException exception) {
        return exception.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors parameterErrors) {
                        return parameterErrors.getFieldErrors().stream()
                                .map(error -> FieldError.of(
                                        error.getField(),
                                        safeReason(error.getDefaultMessage())
                                ));
                    }

                    String field = result.getMethodParameter().getParameterName();
                    String resolvedField = field == null ? DEFAULT_FIELD_NAME : field;
                    return result.getResolvableErrors().stream()
                            .map(error -> FieldError.of(
                                    resolvedField,
                                    safeReason(error.getDefaultMessage())
                            ));
                })
                .toList();
    }

    /**
     * 제약 조건 경로에서 마지막 필드명을 추출한다.
     *
     * @param propertyPath 제약 조건이 발생한 속성 경로
     * @return 클라이언트에게 반환할 필드명
     */
    private String fieldName(String propertyPath) {
        int separatorIndex = propertyPath.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == propertyPath.length() - 1) {
            return propertyPath.isBlank() ? DEFAULT_FIELD_NAME : propertyPath;
        }
        return propertyPath.substring(separatorIndex + 1);
    }

    /**
     * 빈 검증 메시지를 안전한 기본 메시지로 대체한다.
     *
     * @param reason 검증 오류 이유
     * @return 클라이언트에게 반환할 오류 이유
     */
    private String safeReason(String reason) {
        return reason == null || reason.isBlank() ? DEFAULT_FIELD_REASON : reason;
    }
}
