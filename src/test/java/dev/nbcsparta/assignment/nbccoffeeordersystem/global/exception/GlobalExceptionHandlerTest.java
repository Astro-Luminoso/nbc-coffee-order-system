package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 예외 처리기의 HTTP 응답 변환을 검증한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    /**
     * 각 테스트 전에 전역 예외 처리기가 연결된 독립 MockMvc 환경을 구성한다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 서비스 예외를 해당 상태와 표준 오류 응답으로 변환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void handleServiceExceptionReturnsStatusAndDerivedErrorBody() throws Exception {
        mockMvc.perform(get("/exception-test/service").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.data.errors", empty()));
    }

    /**
     * 예상하지 못한 예외가 내부 오류 응답으로 변환되고 세부 메시지를 숨기는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void handleUnexpectedExceptionReturnsGenericInternalServerResponse() throws Exception {
        mockMvc.perform(get("/exception-test/unexpected").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.httpStatus").value(500))
                .andExpect(jsonPath("$.data.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data.message").value(not("민감한 내부 예외 메시지")))
                .andExpect(jsonPath("$.data.errors", empty()));
    }

    /**
     * 예외 변환 검증용 HTTP 요청을 제공한다.
     */
    @RestController
    static class ExceptionTestController {

        /**
         * 서비스 예외를 발생시킨다.
         *
         * @throws InvalidRequestException 요청 값이 잘못된 경우
         */
        @GetMapping("/exception-test/service")
        void throwServiceException() {
            throw new InvalidRequestException("잘못된 요청입니다.");
        }

        /**
         * 예상하지 못한 예외를 발생시킨다.
         */
        @GetMapping("/exception-test/unexpected")
        void throwUnexpectedException() {
            throw new IllegalStateException("민감한 내부 예외 메시지");
        }
    }
}
