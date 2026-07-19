package dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto;

/**
 * API 공통 응답을 표현한다.
 *
 * @param httpStatus HTTP 상태 코드
 * @param data 응답 데이터
 * @param <T> 응답 데이터 타입
 */
public record CommonApiResponse<T>(
        int httpStatus,
        T data
) {

    /**
     * HTTP 상태 코드와 응답 데이터로 공통 응답을 생성한다.
     *
     * @param httpStatus HTTP 상태 코드
     * @param data 응답 데이터
     * @param <T> 응답 데이터 타입
     * @return 생성된 공통 API 응답
     */
    public static <T> CommonApiResponse<T> of(int httpStatus, T data) {
        return new CommonApiResponse<>(httpStatus, data);
    }
}
