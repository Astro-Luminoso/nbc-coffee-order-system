package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service;

/**
 * 포인트 충전 요청의 멱등성 준비 또는 재사용 결과를 표현한다.
 *
 * @param idempotencyKey 포인트 충전 멱등성 키
 * @param completed 저장된 완료 결과의 존재 여부
 * @param httpStatus 저장된 최초 성공 HTTP 상태
 * @param responseBody 저장된 최초 성공 공통 응답 JSON
 */
public record PreparedPointCharge(
        String idempotencyKey,
        boolean completed,
        int httpStatus,
        String responseBody
) {

    /**
     * 새 포인트 충전을 실행할 준비 결과를 생성한다.
     *
     * @param idempotencyKey 포인트 충전 멱등성 키
     * @return 대기 상태 준비 결과
     */
    public static PreparedPointCharge pending(String idempotencyKey) {
        return new PreparedPointCharge(idempotencyKey, false, 0, null);
    }

    /**
     * 저장된 포인트 충전 완료 결과를 생성한다.
     *
     * @param idempotencyKey 포인트 충전 멱등성 키
     * @param httpStatus 최초 성공 HTTP 상태
     * @param responseBody 최초 성공 공통 응답 JSON
     * @return 완료 상태 준비 결과
     */
    public static PreparedPointCharge completed(String idempotencyKey, int httpStatus, String responseBody) {
        return new PreparedPointCharge(idempotencyKey, true, httpStatus, responseBody);
    }
}
